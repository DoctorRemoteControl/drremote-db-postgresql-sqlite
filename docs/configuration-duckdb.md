# DuckDB configuration

ConfigAdmin PID: `de.drremote.db.duckdb`. In Karaf, use `etc/de.drremote.db.duckdb.cfg`.

Start from the [example configuration](../examples/de.drremote.db.duckdb.cfg.example):

```properties
jdbcUrl=jdbc:duckdb:${karaf.data}/drremote.duckdb
poolName=db-duckdb-pool
registerMbeans=false
maxPoolSize=1
minIdle=1
readOnly=false
```

| Property | Default / requirement |
| --- | --- |
| `jdbcUrl` | Required; writable database file path, unless opening an existing database read-only. |
| `poolName` | `db-duckdb-pool` |
| `registerMbeans` | `false`; enable native Hikari JMX management/diagnostics. |
| `maxPoolSize` | Must be `1` |
| `minIdle` | Must be `1` |
| `readOnly` | `false` |
| `tempDirectory` | Optional directory for DuckDB temporary storage. |

Karaf expands `${karaf.data}`. Other OSGi runtimes must provide an appropriate path. DuckDB is an embedded analytical database; native driver/platform support follows DuckDB JDBC. Coordinate writers and do not open competing application instances against the same writable file.

See [pool diagnostics](diagnostics.md) for counters, unique pool names, and JMX-enabled reconfiguration rules.

See [installation](installation.md) and [usage](usage-duckdb.md).
