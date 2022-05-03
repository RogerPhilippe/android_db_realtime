package br.com.phs.sqlrt

interface Logs {

    var tag: String?

    fun i(msg: String)
    fun i(tag: String, msg: String)
    fun w(msg: String)
    fun w(tag: String, msg: String)
    fun e(error: String)
    fun e(tag: String, error: String)
    fun e(exception: Exception)
    fun e(tag: String, exception: Exception)

}