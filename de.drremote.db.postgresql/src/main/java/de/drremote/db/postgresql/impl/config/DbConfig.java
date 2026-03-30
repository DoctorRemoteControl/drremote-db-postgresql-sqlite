package de.drremote.db.postgresql.impl.config;

public final class DbConfig {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    private final int minIdle;

    public DbConfig(
        String jdbcUrl,
        String username,
        String password,
        int maxPoolSize,
        int minIdle
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.maxPoolSize = maxPoolSize;
        this.minIdle = minIdle;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getMinIdle() {
        return minIdle;
    }
}
