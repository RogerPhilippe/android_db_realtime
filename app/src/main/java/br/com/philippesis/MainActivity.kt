package br.com.philippesis

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.phs.sqlrt.ClientConnection

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val logPrefs = LogPrefs()
        logPrefs.tag = "MainActivity"
        val sqliteAdapter = SQLiteAdapter()

        ClientConnection(logPrefs, sqliteAdapter).awaitCommand()

    }

}