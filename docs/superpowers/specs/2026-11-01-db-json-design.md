# Optional JSON database adapter

## Intent and selected approach

The owner asked to implement the proposed optional JSON functionality: query
results as JSON and convenient JSON column parameters/reads. They asked to build
the recommended approach directly. Project content remains English.

Two approaches were considered: adding JSON methods/dependencies to every core,
or supplying one optional adapter on JDBC connections. Use the separate adapter:
all three existing transaction handles already expose `getConnection()`, and the
core bundles can retain their existing dependencies and interfaces.

This increment implements query mapping and JSON column values. The separately
suggested document store is outside this increment.

## API and lifetime

- New bundle `de.drremote.db:json:1.1.0-SNAPSHOT`, symbolic name
  `de.drremote.db.json`, exports `de.drremote.db.json.api`.
- `JsonQueries.on(Connection)` creates a helper on a borrowed connection.
- `ArrayNode query(String sql, Object... parameters)` returns one JSON object per
  result row. `ObjectNode queryOne(...)` returns null for no row and rejects more
  than one row. `int update(...)` returns the JDBC update count.
- `JsonQueries withJsonColumns(String... labels)` returns a new helper declaring
  additional result labels as JSON text. The original helper is unchanged.
- `JsonValue.of(JsonNode)` snapshots a document for parameter binding;
  `JsonValue.parse(String)` validates and snapshots JSON text. `json()` exposes
  the serialized value. Direct `JsonNode` parameters are also supported.
- `JsonDbException` reports JDBC/mapping failures as an unchecked exception.
- The caller owns the connection and transaction. Helpers close their statements
  and result sets, including failure paths, but never close/commit/roll back the
  connection or change its settings.

Example inside any existing transaction callback:

```java
return transactions.inTransaction(db -> JsonQueries.on(db.getConnection())
    .withJsonColumns("payload")
    .query("SELECT id, payload FROM settings WHERE id = ?", id));
```

## Mapping and binding decisions

These implementation choices are the builder's:

- Use Jackson 2.22.4 databind/core and Jackson annotations 2.22 (the versions
  declared by Jackson's published 2.22.4 BOM). Jackson remains exclusive to the
  optional bundle and its Karaf feature.
- Output keys are JDBC column labels, including aliases. Reject blank or duplicate
  labels and declared JSON labels missing from the result, avoiding overwritten
  fields or silently unparsed values.
- Preserve SQL null, booleans, integral and decimal numbers as JSON primitives.
  Reject non-finite numbers. Map dates/times to their ISO Java representations,
  UUIDs to strings, binary values to Base64, and SQL/Java arrays recursively.
  Unsupported JDBC objects require an explicit SQL cast and fail clearly.
- Parse native `json`/`jsonb` result types automatically. SQLite text and text
  projections require `withJsonColumns`, so ordinary text stays text. Invalid JSON
  reports the affected label and preserves the cause.
- JSON parameters are serialized once per value snapshot. Bind PostgreSQL JSON as
  JDBC `OTHER` for server type inference, and SQLite/DuckDB as text parameters.
  Ordinary parameters keep `setObject` behavior. Java null is SQL NULL;
  `JsonValue.parse("null")` and Jackson `NullNode` are the JSON literal null.
- Parsing accepts a single complete JSON value and rejects empty, malformed,
  missing-node, or trailing-document input. JSON floating-point parsing uses
  BigDecimal so JSON values do not lose decimal precision through a double.

## Packaging and compatibility

- Add an independent `json-features` module and `de.drremote-db-json` Karaf
  feature installing the adapter and Jackson bundles. Database features remain
  independently usable without JSON. Use generated, filtered feature versions.
- The JSON bundle depends on JDBC and Jackson; database drivers are test-only
  dependencies. It does not require all three database bundles at runtime.
- Keep Java 17+, current public artifact IDs, APIs, licensing, private checkout,
  and private origin unchanged. Work in the existing isolated public worktree on
  a feature branch based on public main. No private integrations are copied.

## Verification and documentation

- Write tests first and observe missing behavior before implementation.
- Test actual SQLite and DuckDB connections and the existing opt-in PostgreSQL
  service: aliases, primitive types/nulls, exact numbers, empty/single/multiple
  rows, native/text JSON, JSON null versus SQL NULL, parameters, malformed JSON,
  duplicate labels, rollback, and connection/resource ownership on errors.
- Retain the existing database suite. Verify the optional Karaf feature and run
  the complete build on Java 17 and 21. Inspect manifests to confirm existing
  database cores do not import Jackson.
- Add an English guide with copyable transaction examples, installation, type
  mappings, and explicit JSON text column handling; update the README/changelog.
- Request one independent final review, fix important findings with reproducing
  tests, and prepare a GitHub PR. Main-branch integration remains a separate
  approval step, following the established workflow.
