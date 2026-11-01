# DrRemote DB — optional JSON adapter

`de.drremote.db:json:1.1.0-SNAPSHOT` provides JSON query results and JSON parameters
on a caller-owned JDBC connection. Its API is `de.drremote.db.json.api`:

- `JsonQueries.on(connection)` — query, queryOne, and update within your transaction.
- `withJsonColumns(...)` — explicitly parse JSON returned in text columns.
- `JsonValue` — validated JSON parameter snapshots.
- `JsonDbException` — JDBC/mapping failures with their causes.

It uses Jackson 2.22.4 (annotations 2.22), Java 17+, and no database driver at
runtime. Existing database cores remain usable without it.

See the [usage guide](../docs/usage-json.md), [buildable client](../examples/json-client),
and [main README](../README.md). For Karaf, install `de.drremote-db-json` from
`de.drremote.db:json-features:1.1.0-SNAPSHOT` after building from source.

Licensed under [Apache-2.0](../LICENSE).
