package de.drremote.db.postgresql.impl.migration;

public final class PostgresJdbcUrlConverter {

    private PostgresJdbcUrlConverter() {
    }

    /**
     * Converts:
     *   jdbc:postgresql://host:5432/db?x=y
     * to:
     *   postgresql://host:5432/db?x=y
     *
     * If the URL isn't in the expected form, returns it unchanged.
     */
    public static String toPsqlUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }

        String s = jdbcUrl.trim();
        if (!s.startsWith("jdbc:postgresql://")) {
            // Could be jdbc:postgresql:db or something else.
            // We don't try to be clever here.
            return s;
        }

        return "postgresql://" + s.substring("jdbc:postgresql://".length());
    }
}
