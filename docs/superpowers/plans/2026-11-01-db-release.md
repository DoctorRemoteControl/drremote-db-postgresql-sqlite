# DB Reliability and Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline, task by task. Track steps with checkboxes.

**Goal:** Improve JDBC failure handling and single-row queries, expose native pool diagnostics, protect complex psql migrations, and prepare an installable 1.1.0 release.

**Architecture:** Existing JDBC bundles plus optional Hikari JMX registration; unchanged psql execution; a Python standard-library packager builds a whitelisted Maven repository ZIP.

**Tech Stack:** Java 17/21, Maven, JUnit, HikariCP 7.0.2, PostgreSQL/psql, SQLite, DuckDB, Karaf 4.4.11, Python 3.

**Spec:** `docs/superpowers/specs/2026-11-01-db-release-design.md`

## Global constraints

- Preserve private main/remotes and public-only source; reuse detached worktree, create no branches.
- Preserve public JDBC APIs, parameterization, transaction ownership, and all psql APIs/flags.
- English; JMX disabled by default; no remote management listeners.
- Prepare release 1.1.0 and concrete assets; ask only at the final shared-main/publication step.

## Review focus

- Binding failures close statements even for unchecked failures; cleanup cannot hide the original failure.
- Single-row mapping does not traverse/map all rows, changes no SQL, and rejects a second row.
- JMX lifecycle: real registration, unique names, safe rejection of conflicting replacement, disabling, and deactivation cleanup; configuration errors retain the running pool.
- psql remains in production, feature packaging, documentation, and real integration coverage; script failures never record a completed version and transactional scripts roll back.
- Release ZIP includes all internal parent/module POMs and artifact classifiers, no third-party/private cache contents, and works from a fresh consumer/runtime.

## Task 1: JDBC query regression fixes

- [ ] Start only the dedicated temporary PostgreSQL cluster and verify the existing 81-test baseline.
- [ ] Add regression tests for all three runners: zero/one/multiple rows, second-row early rejection, success cleanup, binding failures (SQL/runtime/error), and suppressed cleanup failures. Observe RED.
- [ ] Implement direct `queryOne` iteration and exception-safe binding. Run affected tests GREEN; commit.

## Task 2: Native Hikari diagnostics

- [ ] Add real-pool JMX lifecycle tests for the three data sources (PostgreSQL env-gated). Observe missing-property RED.
- [ ] Pass `registerMbeans` to Hikari, default false. Handle conflicting enabled same-name replacement without disrupting the current pool. Verify enable, counters, safe rejection, distinct-name replacement, disable, and cleanup; commit.
- [ ] Document the property, pool identity/reconfiguration rules, and use of local existing management tools; update example configurations.

## Task 3: Protect complex PostgreSQL scripts

- [ ] Add real psql tests for procedural blocks, meta-commands, COPY input, replay, failed-script rollback/history, and explicit transaction opt-out. Run against the dedicated PostgreSQL instance.
- [ ] Keep production psql behavior unchanged unless a reproduced test requires correction. Update the guide with the JDBC/psql distinction and an example; commit.

## Task 4: Release assets and complete verification

- [ ] Add meaningful packager tests for required-artifact completeness, whitelisted contents/checksums, missing artifacts, and snapshot refusal; observe RED before adding the packager.
- [ ] Prepare version 1.1.0, packager, binary-install guide, release notes, and CI archive verification/upload. Build complete assets; commit.
- [ ] Verify all Java tests/features on Java 17/21 with PostgreSQL enabled; build both examples; run Python tests, docs/link/credential checks, archive integrity checks, fresh-cache consumer build, and fresh Karaf installation from extracted binary repository.
- [ ] Obtain one independent review, fix important findings RED to GREEN, rerun affected/full checks, and record final evidence.
- [ ] Present concrete assets and validation; obtain the final public-main/release publication decision without introducing extra branches.
