# Connection pool diagnostics

All three database ConfigAdmin PIDs accept `registerMbeans`, default `false`.
Set it to `true` to register HikariCP's existing pool and configuration MBeans:

```properties
poolName=my-postgresql-pool
registerMbeans=true
```

Use your runtime's existing JMX access, for example JConsole attached to the
Java process. This property does not start a remote management listener or
change the runtime's authentication. These are Hikari management MBeans, with
its existing configuration/actions, rather than a new read-only HTTP endpoint.

Under `com.zaxxer.hikari`, the pool MBean `type=Pool (my-postgresql-pool)` exposes:

- `ActiveConnections`: connections currently borrowed.
- `IdleConnections`: connections available in the pool.
- `TotalConnections`: the pool's current connection count.
- `ThreadsAwaitingConnection`: callers waiting to borrow a connection.

Counters are momentary observations, collected independently, rather than a
transactionally consistent snapshot. They help investigate pool saturation;
they do not measure individual SQL durations or replace database query analysis.

## Lifecycle and configuration changes

Choose a unique `poolName` for each live JMX-enabled pool in the JVM. MBeans are
removed when their owning pool stops. Disabling `registerMbeans` through
ConfigAdmin removes the previous pool's MBeans and replaces its connections.

Malformed JMX names, wildcard/pattern names, and names already registered by
another live pool are rejected before creating a replacement. The running pool
and other pools' MBeans remain intact after rejection. DrRemote components
coordinate JMX-enabled startup/replacement/stop against the platform MBean
server, including concurrent activations. Validation follows Hikari's selected
MBean naming convention, including its `hikaricp.jmx.register2.0` JVM property.

When replacing a pool that already has JMX enabled, also change `poolName`, or
restart the component before reusing its name. A live enabled replacement with
the same name is rejected before the current pool changes: otherwise Hikari's
startup registration would collide, and closing the old pool would remove the
shared names. The previous pool remains available after that rejected update.
Coordinate pool replacements with callers, as with other connection changes.

PostgreSQL, SQLite, and DuckDB use the same setting and lifecycle rules. Neither
the JDBC API nor the optional JSON adapter requires monitoring to be enabled.

See [HikariCP's management documentation](https://github.com/brettwooldridge/HikariCP/wiki/MBean-%28JMX%29-Monitoring-and-Management)
for the complete native management interface.
