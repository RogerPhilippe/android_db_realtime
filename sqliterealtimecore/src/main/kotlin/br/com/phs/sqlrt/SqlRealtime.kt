package br.com.phs.sqlrt

import android.content.Context
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * Serves a web console (SQL editor, results and terminal) from the device.
 *
 *     val token = SqlRealtime.start(context, MyDbAdapter())
 *
 * The returned token is random on every start and must be typed in the browser.
 * Open http://localhost:8080 (adb forward tcp:8080 tcp:8080) or http://DEVICE_IP:8080.
 */
object SqlRealtime {

    const val DEFAULT_PORT = 8080

    private const val TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val TOKEN_LENGTH = 20

    private var server: WebServer? = null
    private var terminal: Terminal? = null

    /**
     * @param allowWrite when false only SELECT/WITH/PRAGMA(read)/EXPLAIN/VALUES run.
     * @return the access token for this session.
     * @throws java.io.IOException when the port cannot be opened.
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun start(
        context: Context,
        adapter: DBAdapter,
        port: Int = DEFAULT_PORT,
        allowWrite: Boolean = false,
        maxRows: Int = 1000,
        logs: Logs? = null
    ): String {

        stop()

        val token = generateToken()
        val newEngine = QueryEngine(adapter, allowWrite, maxRows)
        val newTerminal = Terminal(logs)
        val newServer = WebServer(port, token, newEngine, context.applicationContext.assets, newTerminal)

        try {
            newServer.start(SOCKET_READ_TIMEOUT, false)
        } catch (ex: Exception) {
            newTerminal.shutdown()
            throw ex
        }

        server = newServer
        terminal = newTerminal

        newTerminal.publish("info", "SQLite Realtime listening on port $port. URLs: ${urls(port).joinToString()}")

        return token

    }

    @JvmStatic
    @Synchronized
    fun stop() {
        server?.stop()
        terminal?.shutdown()
        server = null
        terminal = null
    }

    /** Sends a message to the web terminal (level: info, warn or error). No-op when the server is stopped. */
    @JvmStatic
    @JvmOverloads
    fun log(message: String, level: String = "info") {
        terminal?.publish(level, message, mirror = false)
    }

    /** Addresses where the console can be reached: localhost plus every non-loopback IPv4 of the device. */
    @JvmStatic
    @JvmOverloads
    fun urls(port: Int = DEFAULT_PORT): List<String> {

        val result = mutableListOf("http://localhost:$port")

        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp && !it.isLoopback }?.forEach { nif ->
                nif.inetAddresses.toList()
                    .filter { !it.isLoopbackAddress && it.address.size == 4 }
                    .forEach { result.add("http://${it.hostAddress}:$port") }
            }
        } catch (ex: Exception) {
            // Network listing is informational only.
        }

        return result

    }

    private fun generateToken(): String {
        val random = SecureRandom()
        return buildString {
            repeat(TOKEN_LENGTH) { append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)]) }
        }
    }

    private const val SOCKET_READ_TIMEOUT = 30_000

}
