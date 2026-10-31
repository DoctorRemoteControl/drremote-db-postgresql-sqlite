# Public DB Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the public PostgreSQL, SQLite, and DuckDB project independently buildable, tested, and documented in English.

**Architecture:** Selectively import database code into a branch descended from public GitHub history. Retain the public Maven coordinates and OSGi identities. Use Maven Central for third-party dependencies and source installation for this project's artifacts.

**Tech Stack:** Java 17+, Maven 3.9, OSGi Declarative Services, Apache Karaf, JDBC, HikariCP, JUnit 5, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-01-public-db-refresh-design.md`

## Global Constraints

- English throughout; Apache-2.0 licensing retained.
- Minimum Java 17; public development version `1.1.0-SNAPSHOT`.
- Public artifact IDs: `postgresql`, `postgresql-features`, `sqlite`, `sqlite-features`, `duckdb`, `duckdb-features`, under `de.drremote.db`.
- No private history, Runtime integration, settings, endpoints, deployments, credentials, or service changes.
- Existing public Java packages and bundle symbolic names remain stable.
- No claim that DrRemote artifacts are available on Maven Central or the retired Maven server.

## Review Focus

- Fresh machines: build with empty Maven settings/cache, without sibling projects.
- Embedded persistence: transactions commit and roll back using actual temporary databases.
- Migration replay: repeated migration does not duplicate changes or tracking rows.
- Karaf installation: built feature XML references the actual public artifact IDs/version, including JDBC dependencies.
- Older users: preserve public Maven IDs and explicitly document API/behavior differences.

---

### Task 1: Public database reactor and regression coverage

**Files:** Modify root `pom.xml` and database/feature module POMs; import the three database modules' `src/` trees; add DuckDB JDBC/migration tests under its `src/test/java/`.

**Interfaces:** Consumes the reviewed development database source. Produces six independently public Maven modules at `1.1.0-SNAPSHOT`, with existing API package names and passing tests.

- [x] Record a baseline build with empty Maven settings and a fresh cache.
- [x] Import existing PostgreSQL/SQLite regression tests first; run them against the old public implementation and record expected failure where newer APIs/behavior are absent.
- [x] Import the selected current Java sources and SQL resources, retaining module directories but adapting their POM artifact IDs to the public names.
- [x] Centralize shared Java/dependency/plugin properties in the parent. Ensure DuckDB runs JUnit tests and the public reactor contains no installations module.
- [x] Add focused DuckDB tests for real JDBC commit/rollback and migration replay; verify missing DuckDB implementation fails before importing that module's production code.
- [x] Run the whole reactor tests using the isolated Maven settings/cache; inspect reports and generated OSGi manifests.
- [x] Commit the independently buildable reactor and tests.

### Task 2: Matching Karaf installation descriptors

**Files:** Modify `de.drremote.db.{postgresql,sqlite,duckdb}.features/src/main/feature/feature.xml` and related POMs; add public configuration examples under `examples/`.

**Interfaces:** Consumes Task 1 artifact IDs/version. Produces feature descriptors and example configurations that match those artifacts without private repositories.

- [x] Reproduce any stale artifact/version resolution using the actual feature build or descriptor inspection; retain the evidence.
- [x] Align feature descriptors, drivers, HikariCP, and prerequisites with the reactor. Use Maven filtering or equivalent single-source version values.
- [x] Add portable SQLite, DuckDB, and PostgreSQL `.cfg.example` files containing placeholders only.
- [x] Verify packaged descriptors/manifests; attempt an isolated Karaf SQLite/DuckDB smoke installation if practical.
- [x] Commit the descriptors and examples.

### Task 3: Public docs and CI

**Files:** Rewrite `README.md`; refresh module READMEs and `docs/configuration-*.md`, `docs/usage-*.md`; add `CONTRIBUTING.md`, `CHANGELOG.md`, and `.github/workflows/build.yml`.

**Interfaces:** Consumes Tasks 1–2 commands/artifacts. Produces English source-build, usage, compatibility, contribution, and validation instructions, plus automated JDK 17/21 builds.

- [ ] Document source installation, unavailable hosted DrRemote artifacts, local Maven/Karaf resolution, configuration, and a minimal SQLite example.
- [ ] Import/adapt public database API examples, explain migration limits and compatibility changes, and remove private Runtime references.
- [ ] Add a read-only CI workflow using pinned actions, JDK 17/21 verification, and uploaded JAR/feature outputs; no publishing credentials.
- [ ] Validate local links, sample paths, versions, workflow syntax, and API examples against the implementation.
- [ ] Commit public documentation and CI.

### Task 4: Clean verification, review, and GitHub preparation

**Files:** Final changes across the branch; verification evidence kept outside tracked public content.

**Interfaces:** Consumes all earlier tasks. Produces a reviewed, tested public branch suitable for GitHub review.

- [ ] Build and install from a standalone source export with empty settings and a fresh local Maven cache, so no private artifacts or sibling projects can mask missing dependencies.
- [ ] Inspect test reports, artifacts, feature descriptors, document references, diff whitespace, publication contents, and ancestry.
- [ ] Request a fresh whole-branch review against the spec and this plan. Fix important findings with a failing reproduction followed by passing verification.
- [ ] Prepare GitHub branch/PR details around the final result; publish only the reviewed public branch under the user's authorization. Do not merge, force-push, tag a release, or change the private origin.
