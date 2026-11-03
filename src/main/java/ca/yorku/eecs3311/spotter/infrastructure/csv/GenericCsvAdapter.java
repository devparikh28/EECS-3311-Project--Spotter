package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * Reads an export from an application Spotter
 * does not know, through a mapping a person
 * supplied.
 *
 * The Hevy and Whoop adapters each know one
 * vendor's answer to where the weight lives. This
 * one knows nothing and is told, which is the
 * only honest way to read a file whose format
 * nobody has agreed on.
 *
 * It is the same Adapter as the other two from
 * the outside, which is the point: everything
 * above `DataSource` is unchanged whether the
 * file came from Hevy, from Whoop, or from an
 * application nobody has heard of.
 */
public final class GenericCsvAdapter
        implements DataSource<WorkoutSession> {

    /** Set types treated as not training. */
    private static final List<String> SKIPPED =
            List.of("warmup", "warm up",
                    "warm-up");

    private final ExerciseCatalog catalog;
    private final ColumnMapping mapping;
    private final String sourceName;
    private final List<String> skipped =
            new ArrayList<>();

    public GenericCsvAdapter(
            ExerciseCatalog catalog,
            ColumnMapping mapping) {
        this(catalog, mapping, "mapped CSV");
    }

    public GenericCsvAdapter(
            ExerciseCatalog catalog,
            ColumnMapping mapping,
            String sourceName) {
        if (catalog == null
                || mapping == null) {
            throw new IllegalArgumentException(
                "A catalog and a mapping are"
                + " required");
        }
        this.catalog = catalog;
        this.mapping = mapping;
        this.sourceName = sourceName == null
                || sourceName.isBlank()
                    ? "mapped CSV"
                    : sourceName;
    }

    @Override
    public String name() {
        return sourceName;
    }

    /**
     * True when the mapping fits this file.
     *
     * Not a guess about the format, because there
     * is nothing to guess: a mapped import is
     * readable exactly when the columns it names
     * are present.
     */
    @Override
    public boolean canRead(Path file) {
        try {
            return mapping.validate(
                CsvFileReader.readHeader(file))
                .isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public List<WorkoutSession> read(Path file) {
        skipped.clear();

        Optional<String> problem =
            mapping.validate(
                CsvFileReader.readHeader(file));
        if (problem.isPresent()) {
            throw new IllegalArgumentException(
                    problem.get());
        }

        String dateColumn = column("date");
        String exerciseColumn =
                column("exercise");
        String weightColumn = column("weight");
        String repsColumn = column("reps");

        Map<LocalDate, List<SetEntry>> byDate =
                new LinkedHashMap<>();
        int row = 1;

        for (Map<String, String> values
                : CsvFileReader.read(file)) {
            row++;
            String setType = valueOf(values,
                    mapping.columnFor("set_type")
                        .orElse(null));
            if (isSkippedType(setType)) {
                skipped.add("row " + row
                    + ": a " + setType.trim()
                    + " set is not training"
                    + " data");
                continue;
            }

            LocalDate date = dateOf(
                valueOf(values, dateColumn),
                row);
            if (date == null) {
                continue;
            }

            String name = valueOf(values,
                    exerciseColumn);
            Optional<Exercise> exercise =
                    catalog.byName(name);
            if (exercise.isEmpty()) {
                skipped.add("row " + row
                    + ": \"" + name + "\" is not"
                    + " in the catalog");
                continue;
            }

            Double weight = numberOf(
                valueOf(values, weightColumn));
            Integer reps = wholeOf(
                valueOf(values, repsColumn));
            if (weight == null || reps == null
                    || reps <= 0) {
                skipped.add("row " + row
                    + ": the weight or reps"
                    + " could not be read");
                continue;
            }

            double kg = mapping.unit()
                    .toKg(weight);
            Double rpe = numberOf(valueOf(values,
                mapping.columnFor("rpe")
                    .orElse(null)));

            SetEntry entry = rpe == null
                ? SetEntry.withoutRpe(
                    exercise.get(), kg, reps)
                : SetEntry.of(exercise.get(), kg,
                    reps, rpe);

            byDate.computeIfAbsent(date,
                    key -> new ArrayList<>())
                .add(entry);
        }

        List<WorkoutSession> sessions =
                new ArrayList<>();
        byDate.forEach((date, sets) ->
            sessions.add(new WorkoutSession(0,
                date, sourceName,
                date.toString(), null, sets)));
        return sessions;
    }

    @Override
    public List<String> skipped() {
        return List.copyOf(skipped);
    }

    private String column(String field) {
        return mapping.columnFor(field)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "No column is mapped to "
                    + field));
    }

    private static boolean isSkippedType(
            String setType) {
        if (setType == null
                || setType.isBlank()) {
            return false;
        }
        String lowered = setType.trim()
                .toLowerCase(Locale.ROOT);
        return SKIPPED.contains(lowered);
    }

    private static String valueOf(
            Map<String, String> values,
            String column) {
        if (column == null) {
            return null;
        }
        for (Map.Entry<String, String> entry
                : values.entrySet()) {
            if (entry.getKey().trim()
                    .equalsIgnoreCase(
                        column.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * A date, in the mapped format or the
     * ordinary one.
     *
     * A row whose date cannot be read is skipped
     * with its row number, rather than the import
     * failing, because one bad line in a thousand
     * should not cost a lifter the other nine
     * hundred and ninety nine.
     */
    private LocalDate dateOf(String text,
                             int row) {
        if (text == null || text.isBlank()) {
            skipped.add("row " + row
                    + ": no date");
            return null;
        }
        String trimmed = text.trim();

        Optional<String> format =
                mapping.dateFormat();
        if (format.isPresent()) {
            try {
                return LocalDate.parse(trimmed,
                    DateTimeFormatter.ofPattern(
                        format.get(),
                        Locale.ENGLISH));
            } catch (DateTimeParseException
                    | IllegalArgumentException e) {
                skipped.add("row " + row
                    + ": \"" + trimmed + "\""
                    + " does not match the date"
                    + " format given");
                return null;
            }
        }

        try {
            return LocalDate.parse(
                trimmed.length() > 10
                    ? trimmed.substring(0, 10)
                    : trimmed);
        } catch (DateTimeParseException e) {
            skipped.add("row " + row + ": \""
                + trimmed + "\" is not a date"
                + " Spotter can read. Give a"
                + " date format in the"
                + " mapping.");
            return null;
        }
    }

    private static Double numberOf(
            String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(
                text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer wholeOf(
            String text) {
        Double number = numberOf(text);
        return number == null
                ? null
                : (int) Math.round(number);
    }
}
