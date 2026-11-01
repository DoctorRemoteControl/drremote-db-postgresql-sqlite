# JSON settings client

A buildable OSGi example of JSON results and JSON parameters inside the existing
SQLite transaction service. The migration descriptor creates `json_settings`;
the `JsonSettings` service waits for its `DatabaseReady` marker.

From the repository root:

```sh
mvn clean install
mvn -f examples/json-client/pom.xml clean package
```

Configure/install [SQLite](../../README.md#quick-start-sqlite-in-karaf), then add
the optional JSON feature and this client in Karaf:

```text
feature:repo-add mvn:de.drremote.db/json-features/1.1.0-SNAPSHOT/xml/features
feature:install de.drremote-db-json
bundle:install -s file:/absolute/path/to/checkout/examples/json-client/target/json-client-1.1.0-SNAPSHOT.jar
service:list example.jsonsettings.JsonSettings
```

Other components can reference `JsonSettings` and call `save(String, JsonNode)`,
`load(String)`, or `list()`. A loaded row contains `id` and a parsed `payload`.
Missing rows return Java null. Installing this example creates its table but
does not insert data or start a web interface.

See the [JSON usage guide](../../docs/usage-json.md) for PostgreSQL/DuckDB use,
JSON null handling, and the optional module's connection ownership rules.
