package de.drremote.db.postgresql.impl.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PsqlRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void invokesConfiguredPsqlWithEnvironmentAndPreamble() throws Exception {
        Path record = tempDir.resolve("psql-record.txt");
        Path fakePsql = fakePsql("fake-psql-success", record, 0, "ok");
        Path sqlFile = sqlFile();

        new PsqlRunner().runSqlFile(
            fakePsql.toString(),
            "jdbc:postgresql://localhost:5432/appdb",
            "app_user",
            "secret",
            "app_schema",
            sqlFile,
            false,
            12
        );

        String out = Files.readString(record);
        assertTrue(out.contains("PGUSER=app_user"));
        assertTrue(out.contains("PGPASSWORD=secret"));
        assertTrue(out.contains("PGCONNECT_TIMEOUT=12"));
        assertTrue(out.contains("ARG=-X"));
        assertTrue(out.contains("ARG=-w"));
        assertTrue(out.contains("ARG=-d"));
        assertTrue(out.contains("ARG=postgresql://localhost:5432/appdb"));
        assertTrue(out.contains("CREATE SCHEMA IF NOT EXISTS app_schema"));
        assertTrue(out.contains("SET search_path TO app_schema, core, public"));
        assertTrue(out.contains("ARG=" + sqlFile.toAbsolutePath()));
        assertFalse(out.contains("ARG=-1"));
    }

    @Test
    void quotesNonStandardSchemaNamesInPreamble() {
        String preamble = new PsqlRunner().buildPreamble("123 bad\"schema");

        assertTrue(preamble.contains("CREATE SCHEMA IF NOT EXISTS \"123 bad\"\"schema\""));
        assertTrue(preamble.contains("SET search_path TO \"123 bad\"\"schema\", core, public"));
    }

    @Test
    void includesPsqlOutputWhenCommandFails() throws Exception {
        Path record = tempDir.resolve("psql-record-fail.txt");
        Path fakePsql = fakePsql("fake-psql-fail", record, 7, "boom from psql");

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new PsqlRunner().runSqlFile(
                fakePsql.toString(),
                "jdbc:postgresql://localhost:5432/appdb",
                "app_user",
                "secret",
                "app_schema",
                sqlFile(),
                true,
                30
            )
        );

        assertTrue(ex.getMessage().contains("exit code 7"));
        assertTrue(ex.getMessage().contains("boom from psql"));
    }

    private Path sqlFile() throws IOException {
        Path sqlFile = tempDir.resolve("migration.sql");
        Files.writeString(sqlFile, "CREATE TABLE sample(id integer);");
        return sqlFile;
    }

    private Path fakePsql(String name, Path record, int exitCode, String output) throws IOException {
        boolean windows = isWindows();
        Path script = tempDir.resolve(name + (windows ? ".cmd" : ".sh"));
        Files.writeString(script, windows
            ? windowsScript(record, exitCode, output)
            : unixScript(record, exitCode, output));
        if (!windows) {
            script.toFile().setExecutable(true);
        }
        return script;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String windowsScript(Path record, int exitCode, String output) {
        String recordPath = record.toString();
        return """
            @echo off
            setlocal EnableDelayedExpansion
            echo PGUSER=%%PGUSER%%> "%s"
            echo PGPASSWORD=%%PGPASSWORD%%>> "%s"
            echo PGCONNECT_TIMEOUT=%%PGCONNECT_TIMEOUT%%>> "%s"
            :args
            if "%%~1"=="" goto done
            set "ARG=%%~1"
            echo ARG=!ARG!>> "%s"
            shift
            goto args
            :done
            echo %s
            exit /b %d
            """.formatted(recordPath, recordPath, recordPath, recordPath, output, exitCode);
    }

    private static String unixScript(Path record, int exitCode, String output) {
        return """
            #!/bin/sh
            {
              printf 'PGUSER=%%s\\n' "$PGUSER"
              printf 'PGPASSWORD=%%s\\n' "$PGPASSWORD"
              printf 'PGCONNECT_TIMEOUT=%%s\\n' "$PGCONNECT_TIMEOUT"
              for arg in "$@"; do
                printf 'ARG=%%s\\n' "$arg"
              done
            } > '%s'
            printf '%s\\n'
            exit %d
            """.formatted(record, output, exitCode);
    }
}
