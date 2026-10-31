# PostgreSQL configuration

ConfigAdmin PID: `de.drremote.db.postgresql`. In Karaf, use `etc/de.drremote.db.postgresql.cfg`.

Create a PostgreSQL database and user before installing the feature. For example, an administrator can use PostgreSQL's `createuser --pwprompt app_user` and `createdb --owner=app_user appdb`. Run these against the intended PostgreSQL server with appropriate administrative access.

Copy the [example configuration](../examples/de.drremote.db.postgresql.cfg.example) and replace its password placeholder:

```properties
jdbcUrl=jdbc:postgresql://localhost:5432/appdb
username=app_user
password=REPLACE_WITH_YOUR_DATABASE_PASSWORD
poolName=db-postgresql-pool
maxPoolSize=10
minIdle=1
migrationsEnabled=true
psqlExecutable=psql
psqlConnectTimeoutSeconds=30
```

| Property | Default / requirement |
| --- | --- |
| `jdbcUrl` | Required for the data source. PostgreSQL JDBC URL. |
| `username` | Required for the data source. |
| `password` | Set the database user's actual password for password authentication. |
| `poolName` | `db-postgresql-pool` |
| `maxPoolSize` | `10` |
| `minIdle` | `1` |
| `migrationsEnabled` | `true`; `false` disables automatic and explicit migrations. |
| `psqlExecutable` | `psql`; may be an absolute executable path. |
| `psqlConnectTimeoutSeconds` | `30`; connection timeout for `psql`, not a query execution timeout. |

`jdbcUrl`, `username`, `password`, `migrationsEnabled`, `psqlExecutable`, and `psqlConnectTimeoutSeconds` also support their legacy `dbcore.`-prefixed names. Prefer the short names. Supply a consistent JDBC/migration configuration rather than relying on the migration component's legacy defaults.

The migration service creates the `core` schema and its tracking tables and creates plugin schemas as needed. The database user therefore needs suitable schema creation/object privileges. The JDBC data source does not create the database or database role.

`psql` is needed only for `ScriptMigration` descriptors and must be installed on the machine running the bundle. Script execution passes the password in the child process environment, not in the command-line arguments. Protect your actual configuration using your runtime's normal credential handling and file permissions.

ConfigAdmin applies changes dynamically. Coordinate connection/pool changes and verify migration state before enabling a previously disabled migration service.

See [installation](installation.md) and [usage](usage-postgresql.md).
