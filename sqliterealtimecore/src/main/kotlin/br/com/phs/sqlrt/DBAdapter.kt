package br.com.phs.sqlrt

import android.database.Cursor

/**
 * Bridge between the lib and however the app opens its database (Room, SQLiteOpenHelper, SQLCipher...).
 */
interface DBAdapter {

    /** Runs a statement that returns rows (SELECT, PRAGMA, WITH...). Return null when unavailable. */
    fun executeRawQuery(sql: String): Cursor?

    /**
     * Runs INSERT/UPDATE/DELETE/DDL and returns the number of affected rows.
     * Only called when the server was started with allowWrite = true.
     */
    fun executeUpdate(sql: String): Int =
        throw UnsupportedOperationException("This DBAdapter does not implement executeUpdate.")

}
