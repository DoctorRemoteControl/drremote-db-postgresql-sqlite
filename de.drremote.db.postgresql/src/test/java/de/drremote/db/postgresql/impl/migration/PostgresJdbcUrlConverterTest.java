package de.drremote.db.postgresql.impl.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

final class PostgresJdbcUrlConverterTest {

    @Test
    void convertsStandardJdbcUrlToPsqlUrl() {
        assertEquals(
            "postgresql://localhost:5432/appdb?sslmode=require",
            PostgresJdbcUrlConverter.toPsqlUrl(
                " jdbc:postgresql://localhost:5432/appdb?sslmode=require "
            )
        );
    }

    @Test
    void keepsNonStandardJdbcUrlUnchanged() {
        assertEquals(
            "jdbc:postgresql:appdb",
            PostgresJdbcUrlConverter.toPsqlUrl("jdbc:postgresql:appdb")
        );
    }

    @Test
    void keepsNullAsNull() {
        assertNull(PostgresJdbcUrlConverter.toPsqlUrl(null));
    }
}
