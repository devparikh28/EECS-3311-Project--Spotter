package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a CSV file into rows keyed by column name.
 *
 * Written by hand rather than pulled in as a
 * dependency because the requirement is small and
 * specific: real exports quote fields containing
 * commas, and a date written "28 Oct 2026, 16:17"
 * has one inside the quotes. Splitting on commas
 * would cut that date in half.
 */
public final class CsvFileReader {

    private CsvFileReader() {
    }

    /** The header line, lower cased and trimmed. */
    public static List<String> readHeader(
            Path file) {
        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> header = new ArrayList<>();
        for (String field
                : splitLine(lines.get(0))) {
            header.add(field.trim()
                    .toLowerCase(Locale.ROOT));
        }
        return List.copyOf(header);
    }

    /**
     * Every data row as a map from column name to
     * value. Column names are lower cased so a
     * caller need not match the export's casing.
     */
    public static List<Map<String, String>> read(
            Path file) {
        List<String> lines = readLines(file);
        if (lines.size() < 2) {
            return List.of();
        }
        List<String> header = readHeader(file);
        List<Map<String, String>> rows =
                new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            List<String> values =
                    splitLine(line);
            Map<String, String> row =
                    new LinkedHashMap<>();
            for (int c = 0; c < header.size();
                    c++) {
                row.put(header.get(c),
                    c < values.size()
                        ? values.get(c).trim()
                        : "");
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static List<String> readLines(
            Path file) {
        if (file == null
                || !Files.isRegularFile(file)) {
            throw new CsvFormatException(
                    "No such file: " + file);
        }
        try {
            return Files.readAllLines(file,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CsvFormatException(
                    "Could not read " + file, e);
        }
    }

    /**
     * Splits one line, honouring double quotes.
     * A doubled quote inside a quoted field is a
     * literal quote, as the CSV convention has it.
     */
    static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current =
                new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                boolean escaped = inQuotes
                    && i + 1 < line.length()
                    && line.charAt(i + 1) == '"';
                if (escaped) {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
