## SQLite Usage Examples

SQLite does not support PostgreSQL-style schemas or `search_path`.
Use clear table prefixes per plugin (e.g. `sqlcode_projects`).

### Example 1: DbCore as a lifecycle guarantee

```java
@Component(immediate = true)
public class DbCoreGuard {

    @Reference
    void bind(DbCore core) {
        if (!core.isReady()) {
            throw new IllegalStateException("DbCore not ready");
        }
    }
}
```

### Example 2: Simple repository (read)

```java
@Component
public class ProjectRepository {

    @Reference
    TransactionManager tx;

    public List<String> findAllNames() {
        return tx.inTransaction(h -> h.getQueryRunner().query(
            "SELECT name FROM sqlcode_projects ORDER BY name",
            rs -> rs.getString(1)
        ));
    }
}
```

### Example 3: Insert / update

```java
@Component
public class ProjectWriter {

    @Reference
    TransactionManager tx;

    public void insert(String name) {
        tx.inTransaction(h -> {
            h.getQueryRunner().update(
                "INSERT INTO sqlcode_projects(name) VALUES (?)",
                name
            );
            return null;
        });
    }
}
```

### Example 4: QueryOne

```java
public long countProjects() {
    return tx.inTransaction(h -> h.getQueryRunner().queryOne(
        "SELECT COUNT(*) FROM sqlcode_projects",
        rs -> rs.getLong(1)
    ));
}
```

### Example 5: Domain mapping

```java
record Project(long id, String name) {}

public List<Project> load() {
    return tx.inTransaction(h -> h.getQueryRunner().query(
        "SELECT project_id, name FROM sqlcode_projects",
        rs -> new Project(
            rs.getLong("project_id"),
            rs.getString("name")
        )
    ));
}
```

### Example 6: Explicit rollback

```java
public void failingOp() {
    tx.inTransaction(h -> {
        h.getQueryRunner().update("DELETE FROM sqlcode_projects");
        throw new IllegalStateException("boom");
    });
}
```

### Example 7: Plugin migration descriptor

```java
public final class SqlcodeMigration implements PluginMigrationDescriptor {

    @Override
    public String getPluginName() {
        return "sqlcode";
    }

    @Override
    public List<Migration> getMigrations() {
        return List.of(new Migration() {
            @Override
            public int getVersion() {
                return 1;
            }

            @Override
            public String getDescription() {
                return "initial schema";
            }

            @Override
            public String getSql() {
                return "CREATE TABLE sqlcode_projects (project_id INTEGER PRIMARY KEY, name TEXT NOT NULL)";
            }
        });
    }
}
```

### Example 8: Run migration

```java
@Component(immediate = true)
public class MigrationRunner {

    @Reference
    void run(MigrationService ms) {
        ms.migratePlugin(new SqlcodeMigration());
    }
}
```

### Example 9: Service layer on top of repository

```java
@Component
public class ProjectService {

    @Reference
    ProjectRepository repo;

    public boolean hasProjects() {
        return !repo.findAllNames().isEmpty();
    }
}
```

### Example 10: Pure utility using TransactionCallback

```java
public <T> T withSqlcode(TransactionManager tx, TransactionCallback<T> cb) {
    return tx.inTransaction(h -> cb.doInTransaction(h));
}
```