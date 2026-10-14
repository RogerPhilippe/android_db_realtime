package br.com.phs.sqlrt

import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Event stream shown in the web terminal. Keeps the latest events so a page that
 * connects (or reconnects) can catch up, and mirrors server messages to [Logs].
 */
internal class Terminal(private val logs: Logs?) {

    private val buffer = mutableListOf<String>()
    private val clients = CopyOnWriteArraySet<NanoWSD.WebSocket>()
    private var counter = 0L
    private val keepAlive = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "sqlrt-ws-ping").apply { isDaemon = true }
    }

    init {
        // Keeps idle sockets alive: the browser answers with a pong, which resets the read timeout.
        keepAlive.scheduleWithFixedDelay({
            clients.forEach { client ->
                try {
                    client.ping(ByteArray(0))
                } catch (ex: IOException) {
                    clients.remove(client)
                }
            }
        }, 10, 10, TimeUnit.SECONDS)
    }

    fun shutdown() {
        keepAlive.shutdownNow()
        clients.clear()
    }

    /** level: info | warn | error | sql */
    fun publish(level: String, message: String, detail: String? = null, mirror: Boolean = true) {

        if (mirror) {
            when (level) {
                "info" -> logs?.i(message)
                "warn" -> logs?.w(message)
                "error" -> logs?.e(message)
            }
        }

        val text: String
        synchronized(buffer) {
            counter++
            text = JSONObject()
                .put("id", counter)
                .put("t", System.currentTimeMillis())
                .put("level", level)
                .put("msg", message)
                .put("detail", detail ?: JSONObject.NULL)
                .toString()
            buffer.add(text)
            if (buffer.size > MAX_EVENTS) buffer.removeAt(0)
        }

        clients.forEach { send(it, text) }

    }

    fun attach(client: NanoWSD.WebSocket) {
        clients.add(client)
        val snapshot = synchronized(buffer) { buffer.toList() }
        snapshot.forEach { send(client, it) }
    }

    fun detach(client: NanoWSD.WebSocket) {
        clients.remove(client)
    }

    private fun send(client: NanoWSD.WebSocket, text: String) {
        try {
            client.send(text)
        } catch (ex: IOException) {
            clients.remove(client)
        }
    }

    private companion object {
        const val MAX_EVENTS = 300
    }

}
