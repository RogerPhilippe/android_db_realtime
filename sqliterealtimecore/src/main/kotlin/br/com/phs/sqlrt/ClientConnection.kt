package br.com.phs.sqlrt

import androidx.core.database.getStringOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class ClientConnection() {

    private var defaultIP = "10.0.2.2"
    private var defaultPort = 4500
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var logs: Logs? = null
    private var dbAdapter: DBAdapter? = null

    constructor(logs: Logs?, dbAdapter: DBAdapter?): this() {
        this.logs = logs
        this.dbAdapter = dbAdapter
    }

    fun registerLog(tag: String? = null, logs: Logs?) {
        this.logs = logs
        this.logs?.tag = tag
    }

    fun registerDBAdapter(dbAdapter: DBAdapter?) {
        this.dbAdapter = dbAdapter
    }

    /**
     * Execute with ClientConnection().awaitCommand()
     */
    fun awaitCommand(ip: String? = defaultIP, port: Int? = defaultPort) {
        doInBackground(ip?: defaultIP, port?: defaultPort)
    }

    private fun doInBackground(ip: String, port: Int) {

        Thread {

            try {

                this@ClientConnection.socket = Socket(ip, port)
                this@ClientConnection.socket?.let {
                    this@ClientConnection.writer = PrintWriter(it.getOutputStream(), true)
                    this@ClientConnection.reader = BufferedReader(InputStreamReader(it.getInputStream()))
                }

            } catch (ex: Exception) {
                logs?.e(ex)
            }

            var commandReceived: String

            while (true) {

                if (this@ClientConnection.socket?.isConnected != true) {

                    break
                }

                commandReceived = this@ClientConnection.reader?.readLine()?: continue
                this@ClientConnection.writer?.println(executeRawQuery(commandReceived))

            }

            try {
                this@ClientConnection.socket?.close()
            } catch (ex: Exception) {
                logs?.e(ex)
            }

        }.start()

    }

    private fun executeRawQuery(command: String): String {

        try {

            val cursor = dbAdapter?.executeRawQuery(command) ?: return """{"status":404}"""

            val columnNames = cursor.columnNames
            val jsonArray = JSONArray()
            if (cursor.moveToFirst()) {

                do {

                    val jsonObject = JSONObject()
                    for (columnName in columnNames) {
                        jsonObject.put(columnName?: "UNKNOWN", cursor.getStringOrNull(cursor.getColumnIndex(columnName?: ""))?: "")
                    }

                    jsonArray.put(jsonObject)

                } while (cursor.moveToNext())

                cursor.close()

            }

            val statusCode = if (jsonArray.length() == 0) "204" else "200"

            return """{"status":$statusCode, "columns": ${columnNames.asList()}, "content": $jsonArray}"""

        } catch (ex: Exception) {
            logs?.e(ex)
            return """{"status":500, "content": "${ex.message.toString().replace("\"", "'")}"}"""
        }

    }

}