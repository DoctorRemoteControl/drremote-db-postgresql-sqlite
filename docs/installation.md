# Installation

## Build artifacts locally

Use JDK 17+ and Maven 3.9+ from the repository root:

```sh
mvn clean install
```

The build uses Maven Central for third-party dependencies. The project's own artifacts must be built from source; no public DrRemote Maven server or Maven Central publication is assumed. `install` writes artifacts to your local Maven repository; `verify` builds and checks them without installing.

Generated feature XML is in each `de.drremote.db.<database>.features/target/feature/feature.xml`. Use the generated files, not the templates under `src/main/feature`, which contain Maven placeholders.

## Apache Karaf 4.4.11

Build and install the project as the same operating-system user that runs Karaf. Copy one of the [example configurations](../examples) to the matching filename in Karaf's `etc/` directory before installing its feature.

```text
feature:repo-add mvn:de.drremote.db/sqlite-features/1.1.0-SNAPSHOT/xml/features
feature:install de.drremote-db-sqlite
```

Replace `sqlite` with `postgresql` or `duckdb` as needed. The descriptors install the JDBC driver, HikariCP, and database bundle, with Karaf's `scr` feature as a prerequisite. Feature versions use OSGi notation `1.1.0.SNAPSHOT`; Maven URLs use `1.1.0-SNAPSHOT`.

### Local repository resolution

By default Karaf uses `~/.m2/repository`. If you built with a custom `-Dmaven.repo.local=...`, set `org.ops4j.pax.url.mvn.localRepository` in Karaf's `etc/org.ops4j.pax.url.mvn.cfg` to that absolute directory. A different OS user, container, or remote machine needs access to the installed artifacts too; a local Maven installation does not distribute files to another host.

Karaf can also add a generated descriptor directly:

```text
feature:repo-add file:/absolute/path/to/checkout/de.drremote.db.sqlite.features/target/feature/feature.xml
```

That changes how the descriptor is loaded; its `mvn:` bundle dependencies still need to be resolvable.

See the [Karaf Maven URL handler documentation](https://karaf.apache.org/manual/latest/#_maven_url_handler) for resolver settings.

## Other OSGi runtimes

Install the database bundle, the matching JDBC driver and HikariCP versions from the parent POM, and the runtime services required by their manifests. Enable Declarative Services and provide ConfigAdmin configuration using the documented PID. The Karaf feature files describe the dependency set; they are not a standalone runtime distribution.

## Diagnostics

```text
feature:list -i
bundle:list
scr:list
service:list de.drremote.db.sqlite.api.DbCore
log:tail
```

- **Artifact not found:** run `mvn clean install`, check the exact version, and verify that Karaf sees the same local repository. Do not add the retired DrRemote Maven server.
- **Unresolved bundle:** check `bundle:diag <id>` and confirm the driver/HikariCP versions match the generated descriptor.
- **DataSource activation fails:** check the configuration filename, `jdbcUrl`, file permissions, and PostgreSQL connectivity/credentials.
- **Client remains unsatisfied:** inspect `scr:info <component>`. For PostgreSQL/SQLite clients waiting on `DatabaseReady`, check migration errors and match the descriptor's plugin name to the reference filter.
