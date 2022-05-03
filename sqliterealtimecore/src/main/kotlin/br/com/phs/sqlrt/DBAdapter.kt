package br.com.phs.sqlrt

import android.database.Cursor

interface DBAdapter {

    fun executeRawQuery(sql: String): Cursor?

}