# SQLiteRealtime (Android)

[![](https://jitpack.io/v/RogerPhilippe/android_db_realtime.svg)](https://jitpack.io/#RogerPhilippe/android_db_realtime)

**English** · [Português (BR)](doc/README.pt-BR.md) · [Español](doc/README.es.md)

Android library (`sqliterealtimecore` module + sample app) that serves a web
console from inside your running app, so you can query its SQLite database from
any browser: a SQL editor with syntax highlighting and autocomplete, a results
grid with CSV/JSON export, a table browser and a live terminal with commands
and logs. No desktop client and no sockets to open on your PC.

## How it works

The library starts an HTTP server (NanoHTTPD) inside the app. The browser is
the client, so nothing needs to listen on your computer and firewalls or
antivirus software are not a problem.

## Usage

Implement `DBAdapter` to connect the library to your app's database (Room,
`SQLiteOpenHelper`, SQLCipher, etc.):

    class SQLiteAdapter(private val db: SQLiteDatabase) : DBAdapter {
        override fun executeRawQuery(sql: String): Cursor? = db.rawQuery(sql, null)

        // Only called when allowWrite = true
        override fun executeUpdate(sql: String): Int =
            db.compileStatement(sql).use { it.executeUpdateDelete() }
    }

Start the console:

    val token = SqlRealtime.start(this, SQLiteAdapter(db))   // returns the session token
    Log.i("SQLRT", "Token: $token")

    // when done:
    SqlRealtime.stop()

Optional `start` parameters: `port` (8080), `allowWrite` (false),
`maxRows` (1000) and `logs`.

The token is random and changes on every `start()`. What to do with it is up to
the app (log it, read it at a breakpoint, show it on screen, etc.).
`SqlRealtime.urls()` lists the access addresses and `SqlRealtime.log("msg")`
writes to the page's terminal.

## Access

- **On the device/emulator itself:** open `http://localhost:8080` in its browser.
- **From a PC over USB:** run `adb forward tcp:8080 tcp:8080` and open `http://localhost:8080`.
- **From another PC or phone on the same network:** `http://DEVICE-IP:8080`.

The page asks for the token. While it is valid, the browser logs in
automatically; if the server answers 401 (e.g. the app restarted and the token
changed), the page asks for the token again.

## Security

- A token is required for the whole API and the WebSocket, compared in constant time.
- Temporary lockout after several attempts with a wrong token.
- Cross-site requests are rejected (`Origin` must match `Host`).
- **Read-only** by default: `INSERT`, `UPDATE`, `DELETE` and DDL require
  `allowWrite = true`. The check looks at the first keyword of each statement,
  so do not treat read-only mode as a strong security barrier.
- Traffic is plain HTTP; the token is sent unencrypted. Use it on a trusted
  network and **in debug builds only**.
- `CREATE TRIGGER` with `;` in its body is not supported by the statement splitter.

## Migrating from `ClientConnection`

`ClientConnection` (socket + DBBrowser) was **removed**. `DBAdapter` remains,
now with `executeUpdate`. Replace
`ClientConnection(logs, adapter).awaitCommand()` with
`SqlRealtime.start(context, adapter)`.

## License

This project is dual-licensed:

- **[AGPLv3](LICENSE)** — free to use, modify and redistribute, provided that
  the source code of your modifications is shared under the same license
  (including when the software is offered as a network service).
- **[Commercial license](COMMERCIAL-LICENSE.md)** — for those who cannot or do
  not want to comply with the AGPLv3 (e.g. embedding it in a closed-source
  product, distributing without releasing the source). Contact:
  rogerphilippepereira@gmail.com.

Want to contribute? See [CONTRIBUTING.md](CONTRIBUTING.md).
