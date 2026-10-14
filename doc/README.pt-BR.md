# SQLiteRealtime (Android)

[![](https://jitpack.io/v/RogerPhilippe/android_db_realtime.svg)](https://jitpack.io/#RogerPhilippe/android_db_realtime)

[English](../README.md) · **Português (BR)** · [Español](README.es.md)

Biblioteca Android (módulo `sqliterealtimecore` + app de exemplo) que serve um
console web de dentro do seu app em execução, para consultar o banco SQLite
por qualquer navegador: editor de SQL com destaque de sintaxe e autocomplete,
grade de resultados com exportação CSV/JSON, navegador de tabelas e um terminal
em tempo real com comandos e logs. Sem cliente desktop e sem abrir sockets no PC.

## Como funciona

A lib sobe um servidor HTTP (NanoHTTPD) dentro do app. O navegador é o cliente,
então nada precisa escutar no seu computador e firewall ou antivírus não são
um problema.

## Instalação

Adicione o repositório do JitPack no `settings.gradle`:

    dependencyResolutionManagement {
        repositories {
            google()
            mavenCentral()
            maven { url 'https://jitpack.io' }
        }
    }

Adicione a dependência no `build.gradle` do módulo do seu app:

    dependencies {
        implementation 'com.github.RogerPhilippe:android_db_realtime:v2.0.0'
    }

A lib declara a permissão `INTERNET` no próprio manifest, então não há mais
nada para configurar.

## Uso

Implemente o `DBAdapter` ligando a lib ao banco do seu app (Room,
`SQLiteOpenHelper`, SQLCipher etc.):

    class SQLiteAdapter(private val db: SQLiteDatabase) : DBAdapter {
        override fun executeRawQuery(sql: String): Cursor? = db.rawQuery(sql, null)

        // Só é chamado com allowWrite = true
        override fun executeUpdate(sql: String): Int =
            db.compileStatement(sql).use { it.executeUpdateDelete() }
    }

Inicie o console:

    val token = SqlRealtime.start(this, SQLiteAdapter(db))   // retorna o token da sessão
    Log.i("SQLRT", "Token: $token")

    // ao encerrar:
    SqlRealtime.stop()

Parâmetros opcionais de `start`: `port` (8080), `allowWrite` (false),
`maxRows` (1000) e `logs`.

O token é aleatório e muda a cada `start()`. Quem decide o que fazer com ele é
o app (logar, ler num breakpoint, mostrar na tela etc.). `SqlRealtime.urls()`
lista os endereços de acesso e `SqlRealtime.log("msg")` escreve no terminal da
página.

## Acesso

- **No próprio aparelho/emulador:** abra `http://localhost:8080` no navegador dele.
- **No PC por USB:** `adb forward tcp:8080 tcp:8080` e abra `http://localhost:8080`.
- **Outro PC ou celular na mesma rede:** `http://IP-DO-APARELHO:8080`.

A página pede o token. Enquanto ele for válido, o navegador entra direto; se o
servidor responder 401 (ex.: o app foi reiniciado e o token mudou), a página
volta a pedir o token.

## Segurança

- Token obrigatório em toda a API e no WebSocket, comparado em tempo constante.
- Bloqueio temporário após várias tentativas com token errado.
- Requisições cross-site são recusadas (`Origin` deve bater com o `Host`).
- Por padrão **somente leitura**: `INSERT`, `UPDATE`, `DELETE` e DDL exigem
  `allowWrite = true`. A checagem olha a primeira palavra do comando, então não
  trate o modo leitura como barreira de segurança forte.
- O tráfego é HTTP puro; o token trafega sem criptografia. Use em rede
  confiável e **apenas em builds de debug**.
- `CREATE TRIGGER` com `;` no corpo não é suportado pelo separador de comandos.

## Migração do `ClientConnection`

`ClientConnection` (socket + DBBrowser) foi **removida**. O `DBAdapter`
continua, agora com `executeUpdate`. Troque
`ClientConnection(logs, adapter).awaitCommand()` por
`SqlRealtime.start(context, adapter)`.

## Licença

Este projeto é distribuído sob dupla licença:

- **[AGPLv3](../LICENSE)** — gratuita, para uso, modificação e redistribuição,
  desde que o código-fonte das modificações seja compartilhado sob a mesma
  licença (inclusive se o software for oferecido como serviço em rede).
- **[Licença comercial](../COMMERCIAL-LICENSE.md)** — para quem não pode ou não
  quer cumprir as obrigações da AGPLv3 (ex.: incorporar em produto fechado,
  distribuir sem liberar o código). Entre em contato:
  rogerphilippepereira@gmail.com.

Quer contribuir? Veja [CONTRIBUTING.md](../CONTRIBUTING.md).
