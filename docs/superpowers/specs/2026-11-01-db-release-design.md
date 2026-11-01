# Reliability, diagnostics, and release preparation

The owner approved all three proposed improvements and explicitly requires the
existing PostgreSQL `psql` migration path to remain supported. Project content
stays in English. Work reuses the clean public worktree at public main
`119d3db`; private main and private remotes remain untouched. No new branches.

## Approach

- Keep the JDBC API and improve its internals. `queryOne` maps at most one row
  and detects a second row without building a list; binding failures close the
  prepared statement immediately and preserve the original cause/suppressed
  close failure. Existing transaction ownership and null behavior remain intact.
- Expose Hikari's existing `registerMbeans` property, default false, on all three
  data sources. Do not add a monitoring service or remote JMX listener. Verify
  actual platform MBean registration, counters, disabling, and cleanup. Hikari
  requires unique live pool names: reject a same-name replacement when both old
  and new pools enable JMX before changing the old pool; document changing the
  name or restarting the component for that case. Distinct-name replacements
  and disabling remain supported; default behavior remains unchanged.
- Preserve `ScriptMigration` and `PsqlRunner`. SQL functions/DO blocks can use
  JDBC, but psql meta-commands need the psql client. Add real PostgreSQL/psql
  coverage for dollar-quoted procedural SQL, meta-commands, COPY input, replay,
  failure rollback, and the explicit nontransactional migration option. No
  parser or migration framework replacement.
- Prepare version 1.1.0 with a binary ZIP containing only this project's Maven
  artifacts, feature descriptors, both examples, configuration, documentation,
  and license. Third-party dependencies still resolve from Maven Central;
  PostgreSQL's psql client remains an explicit host prerequisite. Generate
  checksums and exercise installation using the extracted repository without
  relying on locally installed project artifacts.

Native Hikari monitoring avoids a new metrics implementation. A portable Maven
repository ZIP avoids resurrecting the retired server or requiring Maven Central
publishing credentials. Current guides use the release version; historical
plans/specs retain their original recorded versions.

## Verification and boundaries

Observe new regression tests fail before code fixes. Run the complete reactor
with a dedicated PostgreSQL cluster on Java 17 and 21, all feature checks, both
examples, package integrity/whitelist checks, a fresh-cache consumer build, and
an isolated Karaf binary-install smoke test. Obtain one independent final review
and fix important findings with regression tests. Keep all evidence outside
tracked source. Prepare everything before asking for public-main/release
publication approval; do not create a release/tag or push shared main early.
