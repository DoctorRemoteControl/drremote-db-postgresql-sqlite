## SQLite Configuration Example (Karaf)

Configure the SQLite core through a single configuration (PID `de.drremote.db.sqlite`).

> The values below are placeholders only. Do not use them unchanged in production.

### Option 1: `etc/de.drremote.db.sqlite.cfg` file (recommended)

Path:

- `$KARAF_HOME/etc/de.drremote.db.sqlite.cfg`

Content:

```properties
#############################################
# DB.SQLITE Config (PID: de.drremote.db.sqlite)
#############################################

# --- JDBC / SQLite ---
jdbcUrl=jdbc:sqlite:${karaf.data}/drremote.db

# --- HikariCP Pool (SQLite must be 1/1) ---
poolName=db-sqlite-pool
maxPoolSize=1
minIdle=1

# --- SQLite specifics ---
busyTimeoutMs=5000
foreignKeys=true
```

> After saving the file, Karaf will usually pick it up automatically through ConfigAdmin.
> Otherwise, restart Karaf.

---

### Option 2: Configure everything through the Karaf shell (`config:edit`)

```sh
config:edit de.drremote.db.sqlite

# JDBC
config:property-set jdbcUrl jdbc:sqlite:${karaf.data}/drremote.db

# HikariCP
config:property-set poolName db-sqlite-pool
config:property-set maxPoolSize 1
config:property-set minIdle 1

# SQLite specifics
config:property-set busyTimeoutMs 5000
config:property-set foreignKeys true

config:update
```

---

### Optional: Verify the configuration (after setting it)

```sh
config:list "(service.pid=de.drremote.db.sqlite)"

config:edit de.drremote.db.sqlite
config:property-list
```