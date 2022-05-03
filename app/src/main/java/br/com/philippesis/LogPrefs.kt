package br.com.philippesis

import android.util.Log
import br.com.phs.sqlrt.Logs

class LogPrefs: Logs {

    override var tag: String? = "Unknown"

    override fun i(msg: String) {
        Log.i(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun w(msg: String) {
        Log.w(tag, msg)
    }

    override fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    override fun e(error: String) {
        Log.e(tag, error)
    }

    override fun e(tag: String, error: String) {
        Log.e(tag, error)
    }

    override fun e(exception: Exception) {
        Log.e(tag, exception.message?: "UNknown")
    }

    override fun e(tag: String, exception: Exception) {
        Log.e(tag, exception.message?: "UNknown")
    }

}