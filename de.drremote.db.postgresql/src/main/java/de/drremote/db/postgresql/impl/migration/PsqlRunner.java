package de.drremote.db.postgresql.impl.migration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PsqlRunner {

    private static final String DEFAULT_EXECUTABLE = "psql";
    private static final int MAX_CAPTURED_OUTPUT_BYTES = 64 * 1024;

    public void runSqlFile(
            String jdbcUrl,
            String username,
            String password,
            String schemaName,
            Path sqlFile
    ) {
        runSqlFile(DEFAULT_EXECUTABLE, jdbcUrl, username, password, schemaName, sqlFile, true, 30);
    }

    public void runSqlFile(
            String executable,
            String jdbcUrl,
            String username,
            String password,
            String schemaName,
            Path sqlFile,
            boolean singleTransaction,
            int connectTimeoutSeconds
    ) {
        String command = normalizeExecutable(executable);
        String psqlUrl = PostgresJdbcUrlConverter.toPsqlUrl(jdbcUrl);
        if (psqlUrl == null || psqlUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be empty");
        }
        if (sqlFile == null || !Files.isRegularFile(sqlFile)) {
            throw new IllegalArgumentException("sqlFile must point to an existing file");
        }

        // psql keeps a single session for -c and -f in one invocation.
        // So we can create schema + set search_path then execute the file.
        String preamble = buildPreamble(schemaName);

        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.add("-X");                 // do not read ~/.psqlrc (important for reproducibility)
        cmd.add("-w");                 // never wait for an interactive password prompt
        if (singleTransaction) {
            cmd.add("-1");             // single transaction
        }
        cmd.add("-d");
        cmd.add(psqlUrl);
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
        if (connectTimeoutSeconds > 0) {
            pb.environment().put("PGCONNECT_TIMEOUT", String.valueOf(connectTimeoutSeconds));
        }

        pb.redirectErrorStream(true);

        try {
            Process p = pb.start();
            String output = readProcessOutput(p.getInputStream());
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(
                    "psql failed (exit code " + exit + ")" + formatOutput(output)
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "psql could not be started using executable '" + command + "'",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("psql execution interrupted", e);
        }
    }

    private String normalizeExecutable(String executable) {
        if (executable == null || executable.isBlank()) {
            return DEFAULT_EXECUTABLE;
        }
        return executable.trim();
    }

    String buildPreamble(String schemaName) {
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

        // PostgreSQL unquoted identifiers must not start with a digit.
        if (ident.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return ident;
        }

        // otherwise quote and escape "
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private String readProcessOutput(InputStream in) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (total < MAX_CAPTURED_OUTPUT_BYTES) {
                int keep = Math.min(read, MAX_CAPTURED_OUTPUT_BYTES - total);
                captured.write(buffer, 0, keep);
            }
            total += read;
        }

        String output = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        if (total > MAX_CAPTURED_OUTPUT_BYTES) {
            output += System.lineSeparator()
                + "... psql output truncated after " + MAX_CAPTURED_OUTPUT_BYTES + " bytes";
        }
        return output;
    }

    private String formatOutput(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return System.lineSeparator() + output.strip();
    }
}
