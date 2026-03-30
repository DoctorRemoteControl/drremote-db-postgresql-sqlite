package de.drremote.db.postgresql.api;

public class DbCoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DbCoreException(String message) {
        super(message);
    }

    public DbCoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public DbCoreException(Throwable cause) {
        super(cause);
    }
}
