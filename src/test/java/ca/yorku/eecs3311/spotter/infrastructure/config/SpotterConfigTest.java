package ca.yorku.eecs3311.spotter
        .infrastructure.config;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.junit.jupiter.api.Assertions
        .assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for configuration loading.
 *
 * The fixture is a fresh temporary directory per
 * test, so no test can see a file another one
 * wrote.
 */
class SpotterConfigTest {

    @TempDir
    Path tempDir;

    private Path envFile;

    @BeforeEach
    void setUp() {
        envFile = tempDir.resolve(".env");
    }

    private SpotterConfig writeAndLoad(
            String... lines) throws IOException {
        Files.write(envFile, List.of(lines));
        return SpotterConfig.load(envFile);
    }

    @Test
    @DisplayName("reads a plain key and value")
    void readsPlainValue() throws IOException {
        SpotterConfig config = writeAndLoad(
                "LLM_API_KEY=sk-example");

        assertThat(config.hasApiKey()).isTrue();
        assertThat(config.requireApiKey())
                .isEqualTo("sk-example");
    }

    @Test
    @DisplayName("skips comments and blank lines")
    void skipsCommentsAndBlanks()
            throws IOException {
        SpotterConfig config = writeAndLoad(
                "# a comment",
                "",
                "   ",
                "LLM_MODEL=some-model");

        assertThat(config.model())
                .isEqualTo("some-model");
    }

    @Test
    @DisplayName("trims spaces around key and value")
    void trimsSurroundingSpaces()
            throws IOException {
        SpotterConfig config = writeAndLoad(
                "  LLM_MAX_TOKENS  =  2048  ");

        assertThat(config.maxTokens())
                .isEqualTo(2048);
    }

    @Test
    @DisplayName("removes surrounding quotes")
    void removesQuotes() throws IOException {
        SpotterConfig config = writeAndLoad(
                "LLM_MODEL=\"quoted-model\"",
                "SPOTTER_DB_PATH='my.db'");

        assertThat(config.model())
                .isEqualTo("quoted-model");
        assertThat(config.databasePath()
                .toString()).isEqualTo("my.db");
    }

    @Test
    @DisplayName("ignores a line with no equals sign")
    void ignoresMalformedLine()
            throws IOException {
        SpotterConfig config = writeAndLoad(
                "THIS LINE IS NOT A SETTING",
                "LLM_MODEL=still-read");

        assertThat(config.model())
                .isEqualTo("still-read");
    }

    @Test
    @DisplayName("applies defaults when unset")
    void appliesDefaults() throws IOException {
        SpotterConfig config = writeAndLoad(
                "LLM_API_KEY=sk-example");

        assertThat(config.maxTokens())
                .isEqualTo(4096);
        assertThat(config.timeoutSeconds())
                .isEqualTo(60);
        assertThat(config.reasoningModel())
                .contains("sonnet");
    }

    @Test
    @DisplayName("a missing file is not an error")
    void missingFileIsNotAnError() {
        SpotterConfig config = SpotterConfig.load(
                tempDir.resolve("absent"));

        assertThat(config.hasApiKey()).isFalse();
        assertThat(config.maxTokens())
                .isEqualTo(4096);
    }

    @Test
    @DisplayName(
        "the example placeholder is not a key")
    void placeholderIsNotAKey()
            throws IOException {
        SpotterConfig config = writeAndLoad(
          "LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE");

        assertThat(config.hasApiKey()).isFalse();
    }

    @Test
    @DisplayName(
        "a missing key names the file to edit")
    void missingKeyExplainsItself()
            throws IOException {
        SpotterConfig config = writeAndLoad(
                "LLM_MODEL=some-model");

        assertThatThrownBy(config::requireApiKey)
            .isInstanceOf(
                ConfigurationException.class)
            .hasMessageContaining(".env")
            .hasMessageContaining("LLM_API_KEY");
    }

    @Test
    @DisplayName("a bad number fails clearly")
    void badNumberFailsClearly()
            throws IOException {
        SpotterConfig config = writeAndLoad(
                "LLM_MAX_TOKENS=lots");

        ConfigurationException thrown =
            assertThrows(
                ConfigurationException.class,
                config::maxTokens);

        assertThat(thrown.getMessage())
                .contains("LLM_MAX_TOKENS");
    }

    @Test
    @DisplayName("the key never appears in output")
    void keyIsNeverPrinted() throws IOException {
        SpotterConfig config = writeAndLoad(
                "LLM_API_KEY=sk-secret-value");

        assertThat(config.toString())
                .doesNotContain("sk-secret-value")
                .contains("apiKeyPresent=true");
    }

    @Test
    @DisplayName(
        "an unreadable file is reported, not hidden")
    void unreadableFileIsReported()
            throws IOException {
        Path directoryNotFile =
                tempDir.resolve("notAFile");
        Files.createDirectory(directoryNotFile);

        SpotterConfig config =
                SpotterConfig.load(
                        directoryNotFile);

        assertThat(config.hasApiKey()).isFalse();
    }
}
