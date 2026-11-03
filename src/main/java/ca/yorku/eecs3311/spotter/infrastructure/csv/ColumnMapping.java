package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;

/**
 * Which column in somebody else's export means
 * what.
 *
 * Spotter knows Hevy and Whoop by their headers.
 * Everything else is a file with columns in an
 * order nobody agreed on, and the only thing that
 * can resolve that is a person saying which
 * column is the weight.
 *
 * A mapping is therefore a small, saveable
 * answer to that question, and the reason it is
 * saved is that a lifter should answer it once
 * per application rather than once per export.
 */
public final class ColumnMapping {

    /** Fields a workout import cannot do without. */
    public static final List<String> REQUIRED =
            List.of("date", "exercise", "weight",
                    "reps");

    /** Fields that improve an import if present. */
    public static final List<String> OPTIONAL =
            List.of("rpe", "set_type", "notes");

    /**
     * Column names as the file gives them.
     *
     * Every comparison against a header ignores
     * case, because a lifter typing a column
     * name from memory will not match a file's
     * capitalisation and should not have to.
     */
    private final Map<String, String> columns;
    private final WeightUnit unit;
    private final String dateFormat;

    public ColumnMapping(
            Map<String, String> columns,
            WeightUnit unit,
            String dateFormat) {
        this.columns = columns == null
                ? Map.of()
                : Map.copyOf(
                    new LinkedHashMap<>(
                        columns));
        this.unit = unit == null
                ? WeightUnit.KG
                : unit;
        this.dateFormat = dateFormat;
    }

    /** The column holding a field, if mapped. */
    public Optional<String> columnFor(
            String field) {
        return Optional.ofNullable(
                columns.get(field));
    }

    public Map<String, String> columns() {
        return columns;
    }

    public WeightUnit unit() {
        return unit;
    }

    /**
     * The date pattern, when the dates are not
     * in the ordinary form. Empty means
     * YYYY-MM-DD.
     */
    public Optional<String> dateFormat() {
        return Optional.ofNullable(dateFormat)
            .filter(format ->
                    !format.isBlank());
    }

    /**
     * What is still missing.
     *
     * Named fields rather than a boolean, so a
     * lifter is told which column to point at
     * rather than that something is wrong.
     */
    public List<String> missing() {
        List<String> absent = new ArrayList<>();
        for (String field : REQUIRED) {
            if (columnFor(field).isEmpty()) {
                absent.add(field);
            }
        }
        return List.copyOf(absent);
    }

    public boolean isComplete() {
        return missing().isEmpty();
    }

    /**
     * Checks the mapping against a real header.
     *
     * A mapping naming a column the file does not
     * have is worse than an incomplete one: it
     * would read every row as empty and report a
     * successful import of nothing.
     *
     * @return empty when usable, otherwise why
     *         not
     */
    public Optional<String> validate(
            List<String> header) {
        if (!isComplete()) {
            return Optional.of(
                "No column is mapped to "
                + String.join(", ", missing())
                + ".");
        }
        if (header == null
                || header.isEmpty()) {
            return Optional.empty();
        }

        List<String> lowered =
                new ArrayList<>();
        for (String column : header) {
            lowered.add(column.trim()
                .toLowerCase(Locale.ROOT));
        }

        for (Map.Entry<String, String> entry
                : columns.entrySet()) {
            String column = entry.getValue();
            if (column == null
                    || column.isBlank()) {
                continue;
            }
            if (!lowered.contains(column.trim()
                    .toLowerCase(Locale.ROOT))) {
                return Optional.of("The file has"
                    + " no column called \""
                    + column + "\", which was"
                    + " mapped to "
                    + entry.getKey() + ".");
            }
        }
        return Optional.empty();
    }

    /**
     * A first guess from a header, for a lifter
     * to confirm or correct.
     *
     * Deliberately a suggestion rather than a
     * decision. Matching on a column's name is
     * right often enough to save typing and wrong
     * often enough that nobody should import on
     * its say so.
     */
    public static ColumnMapping suggestFor(
            List<String> header) {
        Map<String, String> guessed =
                new LinkedHashMap<>();
        if (header == null) {
            return new ColumnMapping(guessed,
                    WeightUnit.KG, null);
        }

        for (String column : header) {
            String lowered = column.trim()
                    .toLowerCase(Locale.ROOT);
            suggest(guessed, "date", column,
                lowered, "date", "day",
                "start", "timestamp");
            suggest(guessed, "exercise", column,
                lowered, "exercise", "movement",
                "lift", "title");
            suggest(guessed, "weight", column,
                lowered, "weight", "load", "kg",
                "lbs", "lb");
            suggest(guessed, "reps", column,
                lowered, "rep");
            suggest(guessed, "rpe", column,
                lowered, "rpe", "effort");
            suggest(guessed, "set_type", column,
                lowered, "set type",
                "set_type", "type");
            suggest(guessed, "notes", column,
                lowered, "note", "comment");
        }

        WeightUnit unit = WeightUnit.KG;
        String weight = guessed.get("weight");
        if (weight != null) {
            String lowered = weight.toLowerCase(
                    Locale.ROOT);
            if (lowered.contains("lb")
                    || lowered.contains(
                        "pound")) {
                unit = WeightUnit.LB;
            }
        }
        return new ColumnMapping(guessed, unit,
                null);
    }

    private static void suggest(
            Map<String, String> guessed,
            String field,
            String column,
            String lowered,
            String... words) {
        if (guessed.containsKey(field)) {
            return;
        }
        for (String word : words) {
            if (lowered.contains(word)) {
                guessed.put(field, column);
                return;
            }
        }
    }

    /** The mapping as one line, for storage. */
    public String serialise() {
        StringBuilder text =
                new StringBuilder();
        columns.forEach((field, column) ->
            text.append(field).append('=')
                .append(column).append(';'));
        text.append("unit=").append(unit.name())
            .append(';');
        dateFormat().ifPresent(format ->
            text.append("dateFormat=")
                .append(format).append(';'));
        return text.toString();
    }

    /** The mapping back from storage. */
    public static ColumnMapping parse(
            String stored) {
        Map<String, String> columns =
                new LinkedHashMap<>();
        WeightUnit unit = WeightUnit.KG;
        String dateFormat = null;

        if (stored != null) {
            for (String part
                    : stored.split(";")) {
                int at = part.indexOf('=');
                if (at <= 0) {
                    continue;
                }
                String key = part.substring(0,
                        at).trim();
                String value = part.substring(
                        at + 1).trim();
                switch (key) {
                    case "unit" -> {
                        try {
                            unit = WeightUnit
                                .valueOf(value);
                        } catch (
                            IllegalArgumentException
                                e) {
                            unit = WeightUnit.KG;
                        }
                    }
                    case "dateFormat" ->
                        dateFormat = value;
                    default ->
                        columns.put(key, value);
                }
            }
        }
        return new ColumnMapping(columns, unit,
                dateFormat);
    }
}
