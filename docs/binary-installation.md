# Install the binary release

The `drremote-db-1.1.0.zip` release archive contains this project's Maven
repository, source/Javadoc artifacts, four Karaf feature descriptors, both
example bundles and their source, configuration examples, and documentation.
Third-party dependencies download from Maven Central. Project artifacts are
not published to Maven Central; the retired DrRemote Maven server is not used.

## Download and verify

Download `drremote-db-1.1.0.zip` and `SHA256SUMS` from the project's
[GitHub releases](https://github.com/DoctorRemoteControl/drremote-db-postgresql-sqlite/releases).
From the download directory on Linux:

```sh
sha256sum -c SHA256SUMS
unzip drremote-db-1.1.0.zip
export DRREMOTE_RELEASE_DIR="$(pwd)/drremote-db-1.1.0"
```

Keep the extracted directory at a stable absolute path while it is configured
as a repository. Its `manifest.json` records SHA-256 hashes for the packaged
files; Maven artifacts also include SHA-1/SHA-256 sidecars for repository clients.
The archive contains public project artifacts, not a copy of a developer's
Maven cache, database files, configuration credentials, or third-party binaries.

## Karaf

Use Java 17 or later and Karaf 4.4.11. In `etc/org.ops4j.pax.url.mvn.cfg`, add the
extracted repository to the runtime's Maven repositories, retaining Maven Central
for third-party dependencies. Replace the placeholder with its absolute path:

```properties
org.ops4j.pax.url.mvn.repositories = file:/absolute/path/drremote-db-1.1.0/repository@id=drremote-release,https://repo.maven.apache.org/maven2@id=central
```

Copy and edit the configuration example for the desired database:

```sh
cp "$DRREMOTE_RELEASE_DIR/examples/de.drremote.db.sqlite.cfg.example" "$KARAF_HOME/etc/de.drremote.db.sqlite.cfg"
```

Then use the Karaf console:

```text
feature:repo-add mvn:de.drremote.db/sqlite-features/1.1.0/xml/features
feature:install de.drremote-db-sqlite

feature:repo-add mvn:de.drremote.db/json-features/1.1.0/xml/features
feature:install de.drremote-db-json
```

The JSON feature is optional. Replace `sqlite` with `postgresql` or `duckdb` to
select another database. Configure PostgreSQL's server, database, and user
first. PostgreSQL `ScriptMigration` intentionally uses `psql`: install the
PostgreSQL client on the runtime host, or configure `psqlExecutable` with its
absolute path. The release does not bundle that operating-system executable.
See [PostgreSQL usage](usage-postgresql.md) for complex scripts and transaction
boundaries, and [diagnostics](diagnostics.md) for optional native Hikari JMX.

Example JARs are under `examples/notes-client/target` and
`examples/json-client/target`. Install the JSON example after SQLite and JSON:

```text
bundle:install -s file:/absolute/path/drremote-db-1.1.0/examples/json-client/target/json-client-1.1.0.jar
service:list example.jsonsettings.JsonSettings
```

## Maven clients

The supplied settings profile reads `DRREMOTE_RELEASE_DIR` and resolves the
project's parent and module artifacts from its `repository` subdirectory.
For example, rebuild the supplied JSON client using Maven 3.9+ and Java 17+:

```sh
mvn -s "$DRREMOTE_RELEASE_DIR/maven-settings.xml" \
    -f "$DRREMOTE_RELEASE_DIR/examples/json-client/pom.xml" clean verify
```

OSGi clients use a `provided` dependency such as
`de.drremote.db:sqlite:1.1.0`, and optionally `de.drremote.db:json:1.1.0`.
The extracted repository may instead be added as a file repository in your
own Maven/runtime configuration. Its Maven artifacts still depend on public
third-party libraries; this is not a complete offline Karaf distribution.

## Build the archive from source

Maintainers need Python 3.10+ for the standard-library packager. From a complete
source checkout, after configuring a disposable PostgreSQL test database and
installing `psql`:

```sh
mvn clean install
mvn -f examples/notes-client/pom.xml clean verify
mvn -f examples/json-client/pom.xml clean verify
python3 -m unittest discover -s scripts/tests -v
python3 scripts/package_release.py --repository "$HOME/.m2/repository"
```

The ZIP and `SHA256SUMS` are written under `target/release`. Pass the Maven
repository actually used for the build if you selected a different cache.
The packager rejects snapshot versions, missing required artifacts/examples,
and mismatched installed POM coordinates. Packaging does not create a Git tag,
push a branch, or publish a GitHub release.
