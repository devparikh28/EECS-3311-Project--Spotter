package ca.yorku.eecs3311.spotter
        .infrastructure.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Settings the application needs at startup.
 *
 * Values are resolved in this order, first win:
 * an entry in the local .env file, then a real
 * environment variable, then a built in default.
 *
 * The API key deliberately has no default. Code
 * that needs it calls requireApiKey(), which
 * fails with a clear message rather than calling
 * the model with an empty credential.
 */
public final class SpotterConfig {

    public static final String KEY_API =
            "LLM_API_KEY";
    public static final String KEY_MODEL =
            "LLM_MODEL";
    public static final String KEY_MODEL_REASONING =
            "LLM_MODEL_REASONING";
    public static final String KEY_MAX_TOKENS =
            "LLM_MAX_TOKENS";
    public static final String KEY_TIMEOUT =
            "LLM_TIMEOUT_SECONDS";
    public static final String KEY_DB =
            "SPOTTER_DB_PATH";

    private static final String PLACEHOLDER =
            "YOUR_LOCAL_API_KEY_HERE";

    private static final Map<String, String>
            DEFAULTS = Map.of(
            KEY_MODEL,
            "claude-3-5-haiku-20241022",
            KEY_MODEL_REASONING,
            "claude-3-5-sonnet-20241022",
            KEY_MAX_TOKENS, "4096",
            KEY_TIMEOUT, "60",
            KEY_DB, "spotter.db");

    private final Map<String, String> values;

    SpotterConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /** Loads from .env in the working directory. */
    public static SpotterConfig load() {
        return load(Path.of(".env"));
    }

    /**
     * Loads from the given file. A missing file is
     * not an error: environment variables and
     * defaults still apply.
     */
    public static SpotterConfig load(Path envFile) {
        Map<String, String> merged =
                new HashMap<>(DEFAULTS);
        merged.putAll(readEnvironment());
        merged.putAll(parseFile(envFile));
        return new SpotterConfig(merged);
    }

    private static Map<String, String>
            readEnvironment() {
        Map<String, String> found = new HashMap<>();
        for (String key : List.of(
                KEY_API, KEY_MODEL,
                KEY_MODEL_REASONING, KEY_MAX_TOKENS,
                KEY_TIMEOUT, KEY_DB)) {
            String value = System.getenv(key);
            if (value != null
                    && !value.isBlank()) {
                found.put(key, value.trim());
            }
        }
        return found;
    }

    static Map<String, String> parseFile(
            Path envFile) {
        if (envFile == null
                || !Files.isRegularFile(envFile)) {
            return Map.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(
                    envFile,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Could not read "
                            + envFile, e);
        }
        Map<String, String> found = new HashMap<>();
        for (String raw : lines) {
            parseLine(raw).ifPresent(
                    pair -> found.put(
                            pair[0], pair[1]));
        }
        return found;
    }

    /**
     * Reads one KEY=VALUE line. Blank lines and
     * lines starting with # are skipped, as are
     * lines with no equals sign. Surrounding
     * single or double quotes are removed.
     */
    static Optional<String[]> parseLine(
            String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String line = raw.trim();
        if (line.isEmpty()
                || line.startsWith("#")) {
            return Optional.empty();
        }
        int split = line.indexOf('=');
        if (split <= 0) {
            return Optional.empty();
        }
        String key = line.substring(0, split)
                .trim();
        String value = line.substring(split + 1)
                .trim();
        value = stripQuotes(value);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new String[] {key, value});
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"")
                        && v.endsWith("\""))
                    || (v.startsWith("'")
                        && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    public Optional<String> get(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public String getOrDefault(
            String key, String fallback) {
        return get(key).orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        Optional<String> raw = get(key);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.get());
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    key + " must be a whole"
                    + " number, but was "
                    + raw.get());
        }
    }

    /** True when a usable API key is present. */
    public boolean hasApiKey() {
        Optional<String> key = get(KEY_API);
        return key.isPresent()
                && !PLACEHOLDER.equals(key.get());
    }

    /**
     * The API key, or a clear failure. The
     * message names the file to edit and never
     * echoes the value.
     */
    public String requireApiKey() {
        if (!hasApiKey()) {
            throw new ConfigurationException(
                    "No LLM API key found. Copy"
                    + " .env.example to .env and"
                    + " set " + KEY_API
                    + ". Deterministic features"
                    + " still work without it.");
        }
        return get(KEY_API).orElseThrow();
    }

    public String model() {
        return getOrDefault(KEY_MODEL,
                DEFAULTS.get(KEY_MODEL));
    }

    public String reasoningModel() {
        return getOrDefault(KEY_MODEL_REASONING,
                DEFAULTS.get(
                        KEY_MODEL_REASONING));
    }

    public int maxTokens() {
        return getInt(KEY_MAX_TOKENS, 4096);
    }

    public int timeoutSeconds() {
        return getInt(KEY_TIMEOUT, 60);
    }

    public Path databasePath() {
        return Path.of(getOrDefault(
                KEY_DB, "spotter.db"));
    }

    /** Never includes the key itself. */
    @Override
    public String toString() {
        return "SpotterConfig[model="
                + model()
                + ", reasoningModel="
                + reasoningModel()
                + ", maxTokens=" + maxTokens()
                + ", timeoutSeconds="
                + timeoutSeconds()
                + ", databasePath="
                + databasePath()
                + ", apiKeyPresent="
                + hasApiKey() + "]";
    }
}
