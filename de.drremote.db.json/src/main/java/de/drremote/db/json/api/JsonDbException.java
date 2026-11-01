package de.drremote.db.json.api;

/** An error executing a JSON query or converting its JDBC result. */
public final class JsonDbException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JsonDbException(String message) {
        super(message);
    }

    public JsonDbException(String message, Throwable cause) {
        super(message, cause);
    }
}
