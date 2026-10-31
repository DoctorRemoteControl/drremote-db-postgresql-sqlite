# Public DrRemote DB refresh

## Intent

Refresh the existing public `DoctorRemoteControl/drremote-db-postgresql-sqlite`
project so an interested developer can understand, build, test, and use it
without access to the owner's private infrastructure. The public repository has
received external interest but has not been updated since April 2026. The former
public Maven repository no longer exists.

The owner selected PostgreSQL, SQLite, and DuckDB for this refresh and requested
English throughout. Private development remains separate from this public
project.

## Approaches

1. **Refresh PostgreSQL and SQLite only.** Smaller update, but omits the existing
   DuckDB implementation and leaves the public project behind the current DB
   functionality.
2. **Selectively refresh all three database layers. Selected by the owner.**
   Import the relevant current database sources and tests into a branch based on
   the existing public history. Keep the public project independently buildable
   and retain a clear boundary around private infrastructure. This needs more
   validation, particularly for DuckDB, which currently has no tests.

For distribution, the proposed first step is building from public source and
installing the artifacts into the user's local Maven repository. CI provides
reproducible build evidence and downloadable build artifacts. Publishing to
Maven Central or rebuilding a hosted Maven service is outside this refresh;
neither is needed to establish a usable public source project.

## Public contents and boundary

- Include the current PostgreSQL, SQLite, and DuckDB Java implementation,
  database APIs, migration code, and applicable tests, plus their Karaf feature
  modules and public configuration/usage documentation.
- Start from public commit `df992d0276892472310079c9b9ec280b1cb5f106` on isolated
  branch `public/db-refresh`. Import selected database files from the separately
  maintained development source after review.
- Copy selected files rather than merging private commits. No private commit
  history, Git credentials, Maven settings, private endpoints, Eclipse workspace
  state, local deployments, or private Runtime integration is published.
- Exclude `.drremote/`, private deployment examples, and
  `de.drremote.db.installations`. The native Runtime package descriptors require
  a separate, nonpublic DrRemote Runtime and are not the public entry point.
- Preserve the private checkout's branch, files, upstream, and disabled legacy
  push configuration. The public worktree is under the existing OSGi workspace.

## Build and compatibility

Implementation choices proposed by the builder:

- Preserve the existing public Maven IDs `de.drremote.db:postgresql`,
  `postgresql-features`, `sqlite`, and `sqlite-features`. Use `duckdb` and
  `duckdb-features` for the new public modules. The private source's renamed
  artifact IDs are not necessary for this independent public project.
- Preserve the existing exported Java package names and OSGi bundle symbolic
  names. Identify and explain any actual API or behavior incompatibility found
  when importing the newer code.
- Use public development version `1.1.0-SNAPSHOT` consistently across the reactor,
  feature descriptors, examples, and docs. An actual tagged release is a later
  publication decision; do not imply snapshot binaries are remotely published.
- Use Java 17 as the minimum, matching the current database source POMs; validate
  on Java 17 and Java 21 where available. Third-party dependencies and plugins
  must resolve from Maven Central without private settings or cached DrRemote
  artifacts. Reuse compatible existing dependency versions unless a demonstrated
  problem warrants a change.
- Keep common dependency/plugin versions in the parent where useful. Avoid
  unrelated rewrites of the database implementation.
- Make feature descriptors refer to the artifacts and versions actually built.
  Document the local Maven repository used by Karaf and the runtime prerequisites.

## Documentation and project presentation

- Rewrite the English README around purpose, module differences, requirements,
  a working build command, a minimal SQLite quick start, configuration, and
  links to deeper examples.
- Explain that the project's own artifacts must currently be built from source;
  Maven coordinates alone do not imply availability from Maven Central or the
  retired Maven server.
- Align PostgreSQL, SQLite, and DuckDB configuration and usage docs with the
  imported APIs and the new public version. Cover required external PostgreSQL
  and `psql` only where used, embedded database file paths, and actual migration
  limitations.
- Add concise contributor guidance, validation commands, and release/change
  notes for the refresh. Retain Apache-2.0 licensing.
- Add GitHub Actions for build/tests and uploaded build outputs. Avoid deployment
  workflows and private service credentials.

## Validation and acceptance

1. A clean public checkout builds and verifies with an empty local Maven cache
   and empty user/global settings, downloading only public third-party artifacts.
   No private checkout or Runtime source is required.
2. Imported transaction and migration regression tests pass. Add focused embedded
   SQLite and DuckDB tests covering real JDBC access, rollback, and migration
   behavior where this provides missing evidence. Tests use temporary files and
   do not touch the owner's existing databases or services.
3. Verify that packaged bundles contain the expected OSGi metadata and that feature
   XML resolves the public artifact IDs and build version. Run an isolated Karaf
   smoke check if practical; report any runtime validation gap explicitly.
4. Validate documented local file references, versions, and example APIs. Make
   docs consistent about what was tested and which PostgreSQL checks require an
   external database.
5. Inspect the publication diff and the public branch's ancestry for accidental
   private content. Private commits must remain unreachable from this branch.
6. Run a fresh final verification and review. Prepare the changes for GitHub
   review; do not force-push, replace the private origin, create a tagged release,
   or deploy/restart any existing service.

## Left to implementation

The builder may settle CI action pins, supported Maven patch version, the exact
small test fixtures, documentation organization, and the mechanism that keeps
feature versions consistent. Any need for a new public service, private access,
licensing change, or broader publication must be raised separately.
