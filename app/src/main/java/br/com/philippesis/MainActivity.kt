package br.com.philippesis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.phs.sqlrt.SqlRealtime

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // targetSdk 35+ draws edge-to-edge: keep the content clear of the status/navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val logPrefs = LogPrefs()
        logPrefs.tag = "MainActivity"

        val token = SqlRealtime.start(this, SQLiteAdapter(this), allowWrite = true, logs = logPrefs)
        Log.i("MainActivity", "SQLite Realtime token: $token")

        findViewById<TextView>(R.id.urlsText).text = SqlRealtime.urls().joinToString("\n")
        findViewById<TextView>(R.id.tokenText).text = token
        findViewById<Button>(R.id.copyTokenBTN).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SQLite Realtime token", token))
            Toast.makeText(this, "Token copied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        SqlRealtime.stop()
        super.onDestroy()
    }

}
