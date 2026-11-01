# JSON queries and parameters

The optional `de.drremote.db:json:1.1.0-SNAPSHOT` bundle converts JDBC rows to
Jackson JSON nodes and binds JSON values through prepared statements. It works
on the connection supplied by any existing PostgreSQL, SQLite, or DuckDB
transaction callback. The database core bundles have no Jackson dependency.

## Install

Build from source using `mvn clean install`. In Karaf, install your chosen
database feature and the optional JSON feature:

```text
feature:repo-add mvn:de.drremote.db/json-features/1.1.0-SNAPSHOT/xml/features
feature:install de.drremote-db-json
```

This installs the JSON bundle and Jackson databind/core 2.22.4 plus annotations
2.22, as aligned by Jackson's published BOM. It does not install a database
driver or select a database for your application.

Add `de.drremote.db:json:1.1.0-SNAPSHOT` as a `provided` dependency in an OSGi
client. Declare Jackson databind too when your client directly uses Jackson
types. The [JSON settings client](../examples/json-client) contains a complete
POM and two small Declarative Services components.

## Query results as JSON

Import `de.drremote.db.json.api.JsonQueries` and use the existing transaction:

```java
ArrayNode rows = transactions.inTransaction(db ->
    JsonQueries.on(db.getConnection())
        .query("SELECT id, name AS display_name, active FROM customer WHERE id > ?", 0));
```

Create the application's table through its normal migrations first. Each row
becomes an `ObjectNode`; aliases become keys. `query` returns an empty array for
no rows. `queryOne` returns an `ObjectNode` or Java null for no row and throws
`JsonDbException` for multiple rows.

Blank/duplicate column labels are rejected, so joined queries should assign
distinct aliases. Queries materialize their results; use SQL limits/pagination
when selecting a large result set.

## JSON column values

Your migrations choose the column type:

| Database | Example column type | Read behavior |
| --- | --- | --- |
| PostgreSQL | `JSONB` or `JSON` | Parsed automatically from JDBC JSON type metadata. |
| SQLite | `TEXT CHECK(json_valid(payload))` | Declare the returned label using `withJsonColumns("payload")`. |
| DuckDB | `JSON` | Parsed automatically from JDBC JSON type metadata. |

Bind an existing `JsonNode` directly, or validate JSON text with `JsonValue`:

```java
JsonValue payload = JsonValue.parse("{\"enabled\":true,\"tags\":[\"home\",\"office\"]}");

ObjectNode row = transactions.inTransaction(db -> {
    JsonQueries json = JsonQueries.on(db.getConnection());
    json.update("INSERT INTO settings(id, payload) VALUES (?, ?)", "device-42", payload);
    return json.withJsonColumns("payload")
        .queryOne("SELECT id, payload FROM settings WHERE id = ?", "device-42");
});
```

Import `de.drremote.db.json.api.JsonValue` and Jackson's node types in the client.
The example assumes an existing `settings` table. PostgreSQL JSON parameters use
JDBC `OTHER` for server-side type inference; SQLite/DuckDB receive JSON text.
SQL and JSON query operators remain specific to the chosen database.

`JsonValue.of(node)` captures an immutable serialized snapshot, which is useful
when the original node may change later. Direct node parameters are serialized
when the statement is prepared. `JsonValue.parse(text)` accepts exactly one
complete JSON value, including arrays and scalars, and rejects empty/malformed
input or trailing documents.

### JSON null and SQL NULL

| Java parameter | Stored meaning |
| --- | --- |
| Java `null` | SQL NULL. Use `(Object) null` for a single null varargs parameter. |
| `NullNode.instance` or `JsonValue.parse("null")` | JSON literal `null`. |
| Ordinary Java string | Ordinary JDBC string; JSON-looking text is not guessed. |
| `JsonNode` or `JsonValue` | Serialized JSON parameter. |

Both SQL NULL and the JSON null literal appear as JSON null in returned rows.
Select `payload IS NULL AS sql_null` when your application needs to distinguish
their storage meaning.

### Explicit JSON text columns

For SQLite TEXT or a cast of native JSON to text:

```java
JsonQueries json = JsonQueries.on(db.getConnection()).withJsonColumns("payload");
ObjectNode row = json.queryOne("SELECT id, payload FROM settings WHERE id = ?", id);
JsonNode document = row.get("payload");
```

Labels are exact and case-sensitive, including SQL aliases. A missing declared
label or malformed stored JSON raises `JsonDbException`. `withJsonColumns`
returns a new helper and leaves the original unchanged. Ordinary text stays a
JSON string even if it looks like an object, number, or boolean.

## JDBC value mapping

| JDBC value | JSON representation |
| --- | --- |
| SQL NULL | JSON null, with the column key retained. |
| Boolean | JSON boolean. |
| Integral / arbitrary-precision numbers | JSON number, preserving the JDBC numeric value. |
| Decimal | Decimal JSON number; JSON parsing uses BigDecimal rather than double. |
| Date/time/timestamp | ISO-style text; timezone-aware values retain an offset. |
| UUID | String. |
| Binary values | Base64 string. |
| SQL/Java arrays | JSON array, including null entries. |
| Native or explicitly selected JSON | Parsed JSON value. |

NaN/infinity and unsupported JDBC objects fail clearly; use an explicit SQL cast
when your application wants a textual representation instead. Precision already
lost by a database storage type or JDBC driver cannot be recovered by the adapter.

## Transactions and resource ownership

The helper never commits, rolls back, closes its connection, or changes its
settings. Your existing `TransactionManager` owns those decisions. Statements
and result sets are closed on success and failure, including parameter-binding
errors. Let exceptions escape the transaction callback when its work must roll
back.

Create/use a helper within the connection's callback and thread. Do not retain
it after that callback returns. The adapter creates no tables or migrations;
the sample client shows how application-owned migrations and readiness work
alongside it.
