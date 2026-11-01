# SQLite notes client

This small OSGi bundle demonstrates a migration descriptor, `DatabaseReady` activation ordering, and parameterized queries through `TransactionManager`.

From the main repository root:

```sh
mvn clean install
mvn -f examples/notes-client/pom.xml clean package
```

Install/configure the SQLite feature using the [main quick start](../../README.md#quick-start-sqlite-in-karaf), then install this bundle:

```text
bundle:install -s file:/absolute/path/to/checkout/examples/notes-client/target/notes-client-1.1.0.jar
service:list example.notes.NotesRepository
```

[NotesMigrations](src/main/java/example/notes/NotesMigrations.java) creates the table as a versioned migration. [NotesRepository](src/main/java/example/notes/NotesRepository.java) exposes `add(String)` and `list()` to other OSGi components. The service becomes available after migration, but installing the example does not insert sample notes or start a web interface.

The example consumes `de.drremote.db:sqlite:1.1.0` from the local Maven repository created by the root build. It does not require any private infrastructure.
