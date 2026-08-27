# SQLiteRealtime (Android)

Módulo Android (biblioteca `sqliterealtimecore` + app de exemplo) que permite
enviar comandos SQL de um cliente remoto — o
[DBBrowser](https://github.com/RogerPhilippe/DBBrowser) — para serem
executados diretamente contra o banco SQLite de um aplicativo Android em
execução, via conexão por socket.

## Uso

Na `Application`/`Activity`, registre o log e o adapter do banco e inicie a
escuta:

    val logPrefs = LogPrefs()
    logPrefs.tag = "MainActivity"
    val sqliteAdapter = SQLiteAdapter()

    ClientConnection(logPrefs, sqliteAdapter).awaitCommand()

Por padrão, o `ClientConnection` escuta em `10.0.2.2:4500` (endereço padrão
do emulador Android para o host).

## Licença

Este projeto é distribuído sob dupla licença:

- **[AGPLv3](LICENSE)** — gratuita, para uso, modificação e redistribuição,
  desde que o código-fonte das modificações seja compartilhado sob a mesma
  licença (inclusive se o software for oferecido como serviço em rede).
- **[Licença comercial](COMMERCIAL-LICENSE.md)** — para quem não pode ou não
  quer cumprir as obrigações da AGPLv3 (ex.: incorporar em produto fechado,
  distribuir sem liberar o código). Entre em contato:
  rogerphilippepereira@gmail.com.

Quer contribuir? Veja [CONTRIBUTING.md](CONTRIBUTING.md).
