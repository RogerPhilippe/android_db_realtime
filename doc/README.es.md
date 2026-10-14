# SQLiteRealtime (Android)

[![](https://jitpack.io/v/RogerPhilippe/android_db_realtime.svg)](https://jitpack.io/#RogerPhilippe/android_db_realtime)

[English](../README.md) · [Português (BR)](README.pt-BR.md) · **Español**

Biblioteca Android (módulo `sqliterealtimecore` + app de ejemplo) que sirve una
consola web desde dentro de tu app en ejecución, para consultar su base de
datos SQLite desde cualquier navegador: editor de SQL con resaltado de sintaxis
y autocompletado, cuadrícula de resultados con exportación CSV/JSON, explorador
de tablas y una terminal en tiempo real con comandos y logs. Sin cliente de
escritorio y sin abrir sockets en tu PC.

## Cómo funciona

La biblioteca inicia un servidor HTTP (NanoHTTPD) dentro de la app. El
navegador es el cliente, así que nada necesita escuchar en tu computadora y el
firewall o el antivirus no son un problema.

## Instalación

Agrega el repositorio de JitPack en `settings.gradle`:

    dependencyResolutionManagement {
        repositories {
            google()
            mavenCentral()
            maven { url 'https://jitpack.io' }
        }
    }

Agrega la dependencia en el `build.gradle` del módulo de tu app:

    dependencies {
        implementation 'com.github.RogerPhilippe:android_db_realtime:v2.0.0'
    }

La biblioteca declara el permiso `INTERNET` en su propio manifest, así que no
hay nada más que configurar.

## Uso

Implementa `DBAdapter` para conectar la biblioteca con la base de datos de tu
app (Room, `SQLiteOpenHelper`, SQLCipher, etc.):

    class SQLiteAdapter(private val db: SQLiteDatabase) : DBAdapter {
        override fun executeRawQuery(sql: String): Cursor? = db.rawQuery(sql, null)

        // Solo se llama con allowWrite = true
        override fun executeUpdate(sql: String): Int =
            db.compileStatement(sql).use { it.executeUpdateDelete() }
    }

Inicia la consola:

    val token = SqlRealtime.start(this, SQLiteAdapter(db))   // devuelve el token de la sesión
    Log.i("SQLRT", "Token: $token")

    // al terminar:
    SqlRealtime.stop()

Parámetros opcionales de `start`: `port` (8080), `allowWrite` (false),
`maxRows` (1000) y `logs`.

El token es aleatorio y cambia en cada `start()`. Qué hacer con él lo decide la
app (registrarlo en el log, leerlo en un breakpoint, mostrarlo en pantalla,
etc.). `SqlRealtime.urls()` lista las direcciones de acceso y
`SqlRealtime.log("msg")` escribe en la terminal de la página.

## Acceso

- **En el propio dispositivo/emulador:** abre `http://localhost:8080` en su navegador.
- **Desde un PC por USB:** `adb forward tcp:8080 tcp:8080` y abre `http://localhost:8080`.
- **Otro PC o teléfono en la misma red:** `http://IP-DEL-DISPOSITIVO:8080`.

La página pide el token. Mientras sea válido, el navegador entra directamente;
si el servidor responde 401 (p. ej., la app se reinició y el token cambió), la
página vuelve a pedir el token.

## Seguridad

- Token obligatorio en toda la API y en el WebSocket, comparado en tiempo constante.
- Bloqueo temporal tras varios intentos con un token incorrecto.
- Se rechazan las solicitudes cross-site (`Origin` debe coincidir con `Host`).
- Por defecto es **solo lectura**: `INSERT`, `UPDATE`, `DELETE` y DDL requieren
  `allowWrite = true`. La verificación mira la primera palabra del comando, por
  lo que no debes tratar el modo de solo lectura como una barrera de seguridad fuerte.
- El tráfico es HTTP simple; el token viaja sin cifrar. Úsalo en una red de
  confianza y **solo en builds de debug**.
- `CREATE TRIGGER` con `;` en el cuerpo no es compatible con el separador de comandos.

## Migración desde `ClientConnection`

`ClientConnection` (socket + DBBrowser) fue **eliminada**. `DBAdapter` continúa,
ahora con `executeUpdate`. Reemplaza
`ClientConnection(logs, adapter).awaitCommand()` por
`SqlRealtime.start(context, adapter)`.

## Licencia

Este proyecto se distribuye bajo doble licencia:

- **[AGPLv3](../LICENSE)** — gratuita, para uso, modificación y redistribución,
  siempre que el código fuente de las modificaciones se comparta bajo la misma
  licencia (incluso si el software se ofrece como servicio en red).
- **[Licencia comercial](../COMMERCIAL-LICENSE.md)** — para quienes no pueden o
  no quieren cumplir las obligaciones de la AGPLv3 (p. ej., incorporarlo en un
  producto cerrado, distribuir sin liberar el código). Contacto:
  rogerphilippepereira@gmail.com.

¿Quieres contribuir? Consulta [CONTRIBUTING.md](../CONTRIBUTING.md).
