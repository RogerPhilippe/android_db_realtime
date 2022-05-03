package br.com.philippesis

import android.database.Cursor
import br.com.phs.sqlrt.DBAdapter

class SQLiteAdapter: DBAdapter {

    override fun executeRawQuery(sql: String): Cursor? {
        return null
    }

}