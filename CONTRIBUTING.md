# Contributing

Issues and pull requests are welcome. Describe the database, Java/Karaf versions, configuration with credentials removed, expected behavior, actual behavior, and a small reproduction.

## Build and tests

Requirements: JDK 17+ and Maven 3.9+.

```sh
mvn clean verify
mvn install
mvn -f examples/notes-client/pom.xml verify
```

The main suite includes PostgreSQL transaction/URL/script-runner regression tests, SQLite migration/transaction tests, and real temporary DuckDB transaction/migration tests. It does not require a running PostgreSQL server. PostgreSQL's unit tests do not establish compatibility with every live server or workload.

Karaf's Maven plugin verifies all three feature descriptors against Java 17 and Karaf 4.4.11. The GitHub workflow repeats the build on Java 17 and 21. Live Karaf smoke checks are separate from the default Maven suite.

For a clean-room check, create an empty settings file and choose a fresh cache directory:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>
```

```sh
mvn -s /path/to/empty-settings.xml -gs /path/to/empty-settings.xml   -Dmaven.repo.local=/path/to/fresh-cache clean install
```

Use the same settings and cache when building the example. No private parent, account, sibling repository, or DrRemote Runtime is needed.

## Changes

- Keep existing public Maven artifact names and exported API packages stable.
- Add regression tests for changed database behavior; use temporary databases/files.
- Keep Java 17 compatibility and all project content in English.
- Use parameterized SQL for values. Do not add real credentials or deployment state.
- Keep dependency versions and feature descriptors aligned through the parent POM.
- Update configuration/examples and the changelog when behavior changes.

Publishing and release credentials are not part of the build. A successful `mvn install` does not imply a public artifact release.
