package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

/**
 * Thrown when the local database cannot be
 * opened, created or used. Wraps SQLException so
 * nothing above the infrastructure layer has to
 * import java.sql.
 */
public class PersistenceException
        extends RuntimeException {

    private static final long
            serialVersionUID = 1L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
