package de.drremote.db.postgresql.impl.migration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PsqlRunner {

    public void runSqlFile(
            String jdbcUrl,
            String username,
            String password,
            String schemaName,
            Path sqlFile
    ) {
        String psqlUrl = PostgresJdbcUrlConverter.toPsqlUrl(jdbcUrl);

        // psql keeps a single session for -c and -f in one invocation.
        // So we can create schema + set search_path then execute the file.
        String preamble = buildPreamble(schemaName);

        List<String> cmd = new ArrayList<>();
        cmd.add("psql");
        cmd.add(psqlUrl);
        cmd.add("-X");                 // do not read ~/.psqlrc (important for reproducibility)
        cmd.add("-v");
        cmd.add("ON_ERROR_STOP=1");    // fail hard on first error
        cmd.add("-c");
        cmd.add(preamble);
        cmd.add("-f");
        cmd.add(sqlFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);

        // Avoid putting secrets on command line; use env vars.
        if (username != null) {
            pb.environment().put("PGUSER", username);
        }
        if (password != null) {
            pb.environment().put("PGPASSWORD", password);
        }

        pb.inheritIO();

        try {
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("psql failed (exit code " + exit + ")");
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "psql could not be started. Is it installed and on PATH?",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("psql execution interrupted", e);
        }
    }

    private String buildPreamble(String schemaName) {
        String safeSchema = quoteIdent(schemaName);

        // Ensure schema exists (plugins) and set search_path.
        // For core migrations schemaName can be "core".
        return "CREATE SCHEMA IF NOT EXISTS " + safeSchema + "; " +
               "SET search_path TO " + safeSchema + ", core, public;";
    }

    /**
     * Minimal identifier quoting. We only need to support simple schema names like "sqlcode".
     * If schemaName contains weird chars, we quote with ".
     */
    private String quoteIdent(String ident) {
        if (ident == null || ident.isBlank()) {
            throw new IllegalArgumentException("schemaName must not be empty");
        }

        // allow a-z A-Z 0-9 _ only
        if (ident.matches("[A-Za-z0-9_]+")) {
            return ident;
        }

        // otherwise quote and escape "
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
