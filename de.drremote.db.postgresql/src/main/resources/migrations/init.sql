-- =========================================================
-- prep_postgresql_plugin.sql  (psql script)
-- Prepares PostgreSQL for de.drremote.db.postgresql:
--  - creates role (login)
--  - creates database
--  - creates schema "core" owned by role
--  - creates core.schema_migrations
--  - grants + default privileges for future objects
--
-- Run as superuser (e.g. postgres):
--   psql -U postgres -f prep_postgresql_plugin.sql
--
-- Customize via psql variables (top section).
-- =========================================================

\set ON_ERROR_STOP on

-- ====== CONFIG (edit these) ======
\set dbname 'appdb'
\set role   'karaf_postgresql'
\set role_password 'CHANGE_ME'
-- =================================

\echo
\echo '==[1/3] Create role (if missing) =='

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L',
  :'role',
  :'role_password'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = :'role'
)\gexec

\echo
\echo '==[2/3] Create database (if missing) =='

SELECT format(
  'CREATE DATABASE %I OWNER %I',
  :'dbname',
  :'role'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = :'dbname'
)\gexec

\echo
\echo '== Connect to target DB =='
\connect :dbname

\echo
\echo '==[3/3] Grants + core schema + migrations table =='

-- Basic DB privileges
GRANT CONNECT ON DATABASE :"dbname" TO :"role";
GRANT CREATE  ON DATABASE :"dbname" TO :"role";

-- Ensure core schema exists and is owned by role
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'core') THEN
    EXECUTE format('CREATE SCHEMA core AUTHORIZATION %I', :'role');
  ELSE
    EXECUTE format('ALTER SCHEMA core OWNER TO %I', :'role');
  END IF;
END $$;

-- Core schema privileges
GRANT USAGE, CREATE ON SCHEMA core TO :"role";

-- Optional: some tools expect public schema to be usable
GRANT USAGE, CREATE ON SCHEMA public TO :"role";

-- Migration table (central)
CREATE TABLE IF NOT EXISTS core.schema_migrations (
  plugin_name  TEXT    NOT NULL,
  schema_name  TEXT    NOT NULL,
  version      INTEGER NOT NULL,
  description  TEXT    NOT NULL,
  sql          TEXT,
  applied_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (plugin_name, version)
);

-- Ensure owner + grants on migration table
ALTER TABLE core.schema_migrations OWNER TO :"role";
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE core.schema_migrations TO :"role";

-- Default privileges for objects created by the role inside schema core
-- (important so future tables/sequences are usable without extra GRANTs)
ALTER DEFAULT PRIVILEGES FOR ROLE :"role" IN SCHEMA core
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"role";

ALTER DEFAULT PRIVILEGES FOR ROLE :"role" IN SCHEMA core
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO :"role";

\echo
\echo '== Smoke test =='

SELECT nspname AS schema_ok
FROM pg_namespace
WHERE nspname IN ('core','public');

SELECT * FROM core.schema_migrations ORDER BY applied_at DESC LIMIT 5;

\echo
\echo '== Done. Database prepared for de.drremote.db.postgresql =='
