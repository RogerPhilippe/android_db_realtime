package br.com.phs.sqlrt

import android.content.res.AssetManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class WebServer(
    port: Int,
    private val token: String,
    private val engine: QueryEngine,
    private val assets: AssetManager,
    private val terminal: Terminal
) : NanoWSD(port) {

    private class Attempts(var failures: Int = 0, var lockedUntil: Long = 0)

    private val attempts = ConcurrentHashMap<String, Attempts>()

    override fun serve(session: IHTTPSession): Response {

        if (!isWebsocketRequested(session)) return super.serve(session)

        // Browsers cannot set headers on a WebSocket, so the token travels in the query string.
        if (session.uri != "/ws") return json(Response.Status.NOT_FOUND, error("Not found."))
        if (!originAllowed(session)) return json(Response.Status.FORBIDDEN, error("Origin not allowed."))
        checkToken(session, session.parameters["token"]?.firstOrNull() ?: "")?.let { return it }
        return super.serve(session)

    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = Client(handshake)

    private inner class Client(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() = terminal.attach(this)
        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) = terminal.detach(this)
        override fun onMessage(message: WebSocketFrame?) {}
        override fun onPong(pong: WebSocketFrame?) {}
        override fun onException(exception: IOException?) = terminal.detach(this)
    }

    override fun serveHttp(session: IHTTPSession): Response {

        return try {

            val path = session.uri

            if (path == "/" || path == "/index.html") {
                if (session.method != Method.GET) return json(Response.Status.METHOD_NOT_ALLOWED, error("Method not allowed."))
                return asset("sqlrt/index.html", "text/html; charset=utf-8")
            }

            if (!path.startsWith("/api/")) return json(Response.Status.NOT_FOUND, error("Not found."))

            if (!originAllowed(session)) return json(Response.Status.FORBIDDEN, error("Origin not allowed."))

            val header = session.headers["authorization"] ?: ""
            val supplied = if (header.startsWith("Bearer ")) header.substring(7).trim() else ""
            checkToken(session, supplied)?.let { return it }

            when {
                path == "/api/auth" && session.method == Method.GET ->
                    json(Response.Status.OK, JSONObject().put("ok", true))

                path == "/api/schema" && session.method == Method.GET ->
                    json(Response.Status.OK, engine.schema())

                path == "/api/query" && session.method == Method.POST -> {
                    val sql = readSql(session)
                        ?: return json(Response.Status.BAD_REQUEST, error("Invalid body. Expected: {\"sql\": \"...\"}."))
                    val result = engine.execute(sql)
                    publishQuery(result, session.remoteIpAddress)
                    json(Response.Status.OK, result)
                }

                else -> json(Response.Status.NOT_FOUND, error("Not found."))
            }

        } catch (ex: Exception) {
            terminal.publish("error", ex.message ?: ex.javaClass.simpleName)
            json(Response.Status.INTERNAL_ERROR, error(ex.message ?: "Internal error."))
        }

    }

    /** Returns null when the request carries a valid token, otherwise the error response to send. */
    private fun checkToken(session: IHTTPSession, supplied: String): Response? {

        val ip = session.remoteIpAddress ?: "unknown"
        val state = attempts.getOrPut(ip) { Attempts() }
        val now = System.currentTimeMillis()

        if (state.lockedUntil > now) {
            val wait = (state.lockedUntil - now + 999) / 1000
            return json(TooManyRequests, error("Too many attempts. Wait ${wait}s.")).also {
                it.addHeader("Retry-After", wait.toString())
            }
        }

        if (supplied.isNotEmpty() && constantTimeEquals(supplied, token)) {
            state.failures = 0
            return null
        }

        // Only a wrong token counts as an attempt; a missing one is just the page asking before login.
        if (supplied.isNotEmpty()) {
            state.failures++
            if (state.failures >= MAX_FAILURES) {
                val backoff = BASE_LOCK_MS shl minOf(state.failures - MAX_FAILURES, 5)
                state.lockedUntil = now + minOf(backoff, MAX_LOCK_MS)
                terminal.publish("warn", "Invalid token from $ip (${state.failures} failures). Temporarily blocked.")
            }
        }

        return json(Response.Status.UNAUTHORIZED, error("Invalid or missing token."))

    }

    /** Blocks cross-site requests: when Origin is present its host:port must match the Host header. */
    private fun originAllowed(session: IHTTPSession): Boolean {
        val origin = session.headers["origin"] ?: return true
        val host = session.headers["host"] ?: return false
        return origin.substringAfter("://", origin).equals(host, ignoreCase = true)
    }

    private fun publishQuery(result: JSONObject, ip: String?) {
        val from = ip?.let { " ($it)" } ?: ""
        val items = result.getJSONArray("results")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val ms = item.optLong("timeMs")
            when (item.getString("type")) {
                "rows" -> {
                    val n = item.getJSONArray("rows").length()
                    val more = if (item.optBoolean("truncated")) "+" else ""
                    terminal.publish("sql", item.getString("sql"), "$n$more rows • ${ms}ms$from")
                }
                "update" -> terminal.publish("sql", item.getString("sql"), "${item.getInt("affected")} affected • ${ms}ms$from")
                else -> {
                    terminal.publish("sql", item.getString("sql"), "$from".trim().ifEmpty { null })
                    terminal.publish("error", item.getString("message"), mirror = false)
                }
            }
        }
    }

    private fun readSql(session: IHTTPSession): String? {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: return null
        return try {
            JSONObject(body).getString("sql")
        } catch (ex: Exception) {
            null
        }
    }

    private fun asset(name: String, mime: String): Response =
        newChunkedResponse(Response.Status.OK, mime, assets.open(name)).also {
            it.addHeader("Cache-Control", "no-store")
        }

    private fun json(status: Response.IStatus, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()).also {
            it.addHeader("Cache-Control", "no-store")
        }

    private fun error(message: String) = JSONObject().put("error", message)

    private fun constantTimeEquals(a: String, b: String) =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private companion object {
        const val MAX_FAILURES = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 10 * 60_000L
    }

}

// NanoHTTPD has no 429 constant.
private object TooManyRequests : NanoHTTPD.Response.IStatus {
    override fun getDescription() = "429 Too Many Requests"
    override fun getRequestStatus() = 429
}
