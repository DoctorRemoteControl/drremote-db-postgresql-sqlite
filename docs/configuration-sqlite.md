# SQLite configuration

ConfigAdmin PID: `de.drremote.db.sqlite`. In Karaf, use `etc/de.drremote.db.sqlite.cfg`.

Copy the [complete example](../examples/de.drremote.db.sqlite.cfg.example) before installing the feature:

```properties
jdbcUrl=jdbc:sqlite:${karaf.data}/drremote.sqlite
poolName=db-sqlite-pool
registerMbeans=false
maxPoolSize=1
minIdle=1
busyTimeoutMs=5000
foreignKeys=true
```

| Property | Default / requirement |
| --- | --- |
| `jdbcUrl` | Required; JDBC SQLite URL. The process needs access to the parent directory/file. |
| `poolName` | `db-sqlite-pool` |
| `registerMbeans` | `false`; enable native Hikari JMX management/diagnostics. |
| `maxPoolSize` | Must be `1` |
| `minIdle` | Must be `1` |
| `busyTimeoutMs` | `5000` milliseconds |
| `foreignKeys` | `true` |

`${karaf.data}` is expanded by Karaf configuration handling. In another OSGi runtime, provide an actual writable path or that runtime's supported property substitution.

SQLite is embedded; no server or password is required. Avoid two application instances writing the same file. Configuration updates replace the pool, so coordinate changes with callers.

See [pool diagnostics](diagnostics.md) for counters, unique pool names, and JMX-enabled reconfiguration rules.

See [installation](installation.md) and [usage](usage-sqlite.md).
