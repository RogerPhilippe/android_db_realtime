package br.com.phs.sqlrt

import android.database.Cursor
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes SQL text received from the web console against a [SQLiteDatabase].
 * Calls are serialized, so only one statement touches the database at a time.
 */
internal class QueryEngine(
    private val adapter: DBAdapter,
    private val allowWrite: Boolean,
    private val maxRows: Int
) {

    private val lock = Any()
    fun execute(sqlText: String): JSONObject {

        val results = JSONArray()

        synchronized(lock) {
            for (statement in splitStatements(sqlText)) {
                val result = runStatement(statement)
                results.put(result)
                if (result.getString("type") == "error") break
            }
        }

        return JSONObject().put("results", results)

    }

    fun schema(): JSONObject {

        val tables = JSONArray()

        synchronized(lock) {

            val names = mutableListOf<Pair<String, String>>()
            query(
                "SELECT name, type FROM sqlite_master WHERE type IN ('table','view') " +
                        "AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' ORDER BY name"
            ).use { c ->
                while (c.moveToNext()) names.add(c.getString(0) to c.getString(1))
            }

            for ((name, type) in names) {

                val columns = JSONArray()
                query("PRAGMA table_info(${quoteIdentifier(name)})").use { c ->
                    while (c.moveToNext()) {
                        columns.put(
                            JSONObject()
                                .put("name", c.getString(c.getColumnIndexOrThrow("name")))
                                .put("type", c.getString(c.getColumnIndexOrThrow("type")))
                                .put("notNull", c.getInt(c.getColumnIndexOrThrow("notnull")) == 1)
                                .put("pk", c.getInt(c.getColumnIndexOrThrow("pk")) > 0)
                        )
                    }
                }

                tables.put(JSONObject().put("name", name).put("type", type).put("columns", columns))

            }

        }

        return JSONObject().put("tables", tables)

    }

    private fun runStatement(sql: String): JSONObject {

        val started = System.currentTimeMillis()
        val keyword = firstKeyword(sql)

        return try {

            val isRead = keyword in READ_KEYWORDS && !(keyword == "PRAGMA" && sql.contains('='))

            if (!isRead && !allowWrite) {
                return error(sql, "Write statements are disabled (allowWrite = false).")
            }

            val result = when {
                isRead -> runQuery(sql)
                else -> JSONObject().put("type", "update").put("affected", adapter.executeUpdate(sql))
            }

            result.put("sql", sql).put("timeMs", System.currentTimeMillis() - started)

        } catch (ex: Exception) {
            error(sql, ex.message ?: ex.javaClass.simpleName)
        }

    }

    private fun runQuery(sql: String): JSONObject {

        query(sql).use { cursor ->

            val columns = JSONArray()
            for (name in cursor.columnNames) columns.put(name)

            val rows = JSONArray()
            var truncated = false

            while (cursor.moveToNext()) {
                if (rows.length() >= maxRows) {
                    truncated = true
                    break
                }
                rows.put(readRow(cursor))
            }

            return JSONObject()
                .put("type", "rows")
                .put("columns", columns)
                .put("rows", rows)
                .put("truncated", truncated)

        }

    }

    private fun query(sql: String): Cursor =
        adapter.executeRawQuery(sql) ?: throw IllegalStateException("The DBAdapter returned null for the query.")

    private fun readRow(cursor: Cursor): JSONArray {

        val row = JSONArray()

        for (i in 0 until cursor.columnCount) {
            when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> row.put(JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> row.put(cursor.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> {
                    val d = cursor.getDouble(i)
                    if (d.isNaN() || d.isInfinite()) row.put(d.toString()) else row.put(d)
                }
                Cursor.FIELD_TYPE_BLOB ->
                    row.put(JSONObject().put("\$blob", Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP)))
                else -> row.put(cursor.getString(i))
            }
        }

        return row

    }

    private fun error(sql: String, message: String): JSONObject =
        JSONObject().put("type", "error").put("sql", sql).put("message", message)

    companion object {

        private val READ_KEYWORDS = setOf("SELECT", "WITH", "PRAGMA", "EXPLAIN", "VALUES")

        fun quoteIdentifier(name: String) = "\"" + name.replace("\"", "\"\"") + "\""

        /** Returns the first SQL keyword, skipping whitespace and comments. */
        fun firstKeyword(sql: String): String {

            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                when {
                    c.isWhitespace() -> i++
                    sql.startsWith("--", i) -> {
                        val end = sql.indexOf('\n', i)
                        i = if (end == -1) sql.length else end + 1
                    }
                    sql.startsWith("/*", i) -> {
                        val end = sql.indexOf("*/", i + 2)
                        i = if (end == -1) sql.length else end + 2
                    }
                    else -> break
                }
            }

            var j = i
            while (j < sql.length && sql[j].isLetter()) j++
            return sql.substring(i, j).uppercase()

        }

        /**
         * Splits on ';' outside of strings, quoted identifiers and comments.
         * Statements with no keyword (blank or comment only) are dropped.
         * Note: CREATE TRIGGER bodies containing ';' are not supported.
         */
        fun splitStatements(sql: String): List<String> {

            val statements = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0

            fun flush() {
                val text = current.toString().trim()
                if (text.isNotEmpty() && firstKeyword(text).isNotEmpty()) statements.add(text)
                current.setLength(0)
            }

            while (i < sql.length) {

                val c = sql[i]

                when {
                    c == '\'' || c == '"' || c == '`' -> {
                        val end = closingQuote(sql, i, c)
                        current.append(sql, i, end)
                        i = end
                    }
                    c == '[' -> {
                        val close = sql.indexOf(']', i)
                        val end = if (close == -1) sql.length else close + 1
                        current.append(sql, i, end)
                        i = end
                    }
                    sql.startsWith("--", i) -> {
                        val nl = sql.indexOf('\n', i)
                        val end = if (nl == -1) sql.length else nl
                        current.append(sql, i, end)
                        i = end
                    }
                    sql.startsWith("/*", i) -> {
                        val close = sql.indexOf("*/", i + 2)
                        val end = if (close == -1) sql.length else close + 2
                        current.append(sql, i, end)
                        i = end
                    }
                    c == ';' -> {
                        flush()
                        i++
                    }
                    else -> {
                        current.append(c)
                        i++
                    }
                }

            }

            flush()
            return statements

        }

        /** Index right after the quote that closes the one opened at [start]; '' style escapes are honored. */
        private fun closingQuote(sql: String, start: Int, quote: Char): Int {
            var i = start + 1
            while (i < sql.length) {
                if (sql[i] == quote) {
                    if (i + 1 < sql.length && sql[i + 1] == quote) i += 2 else return i + 1
                } else i++
            }
            return sql.length
        }

    }

}
