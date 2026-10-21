package ca.yorku.eecs3311.spotter
        .infrastructure.config;

/**
 * Thrown when configuration needed to run is
 * missing or unusable. The message never
 * contains the value of a secret.
 */
public class ConfigurationException
        extends RuntimeException {

    private static final long
            serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
