## Full Configuration Example (Karaf)

You can configure the PostgreSQL core completely through **a single** configuration (PID `de.drremote.db.postgresql`).

> This file covers PostgreSQL only. For SQLite configuration, see `docs/configuration-sqlite.md`.

> The values below are placeholders only. Do not use them unchanged in production.

### Option 1: `etc/de.drremote.db.postgresql.cfg` file (recommended)

Path:

- `$KARAF_HOME/etc/de.drremote.db.postgresql.cfg`

Content (example using database `appdb` and user `karaf_postgresql`):

```properties
#############################################
# DB.POSTGRESQL Config (PID: de.drremote.db.postgresql)
#############################################

# --- JDBC / PostgreSQL ---
jdbcUrl=jdbc:postgresql://localhost:5432/appdb
username=karaf_postgresql
password=change_me

# --- HikariCP Pool ---
poolName=db-postgresql-pool
maxPoolSize=10
minIdle=1

#############################################
# Optional: legacy keys (only if needed)
# (these are only used if the "new" keys
#  above are NOT set)
#############################################
# dbcore.jdbcUrl=jdbc:postgresql://localhost:5432/appdb
# dbcore.username=karaf_postgresql
# dbcore.password=change_me
```

> After saving the file, Karaf will usually pick it up automatically through ConfigAdmin.
> Otherwise, restart Karaf.

---

### Option 2: Configure everything through the Karaf shell (`config:edit`)

```sh
config:edit de.drremote.db.postgresql

# JDBC
config:property-set jdbcUrl jdbc:postgresql://localhost:5432/appdb
config:property-set username karaf_postgresql
config:property-set password change_me

# HikariCP
config:property-set poolName db-postgresql-pool
config:property-set maxPoolSize 10
config:property-set minIdle 1

config:update
```

---

### Optional: Verify the configuration (after setting it)

```sh
config:list "(service.pid=de.drremote.db.postgresql)"

config:edit de.drremote.db.postgresql
config:property-list
```
