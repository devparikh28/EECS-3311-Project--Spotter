package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

/** Thrown when a file cannot be read as CSV. */
public class CsvFormatException
        extends RuntimeException {

    private static final long
            serialVersionUID = 1L;

    public CsvFormatException(String message) {
        super(message);
    }

    public CsvFormatException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
