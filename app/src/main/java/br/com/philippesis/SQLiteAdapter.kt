package br.com.philippesis

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import br.com.phs.sqlrt.DBAdapter

class SQLiteAdapter(context: Context) : DBAdapter {

    private val db: SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(
        context.getDatabasePath("sample.db").also { it.parentFile?.mkdirs() }, null
    ).also {
        it.execSQL("CREATE TABLE IF NOT EXISTS person (id INTEGER PRIMARY KEY, name TEXT NOT NULL, age INTEGER)")
    }

    override fun executeRawQuery(sql: String): Cursor {
        return db.rawQuery(sql, null)
    }

    override fun executeUpdate(sql: String): Int {
        return db.compileStatement(sql).use { it.executeUpdateDelete() }
    }

}
