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
        .WeightUnit;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * Reads a Hevy workout export.
 *
 * Written against a real export rather than an
 * assumed format, which changed three things.
 *
 * Weights are in pounds, so every figure is
 * converted; without that every downstream number
 * would be wrong by a factor of 2.2.
 *
 * There are four set types, not two. A warm up is
 * not training data and is skipped. A set marked
 * "failure" carries no RPE in the file but is by
 * definition RPE 10, so it is read as one. In a
 * real export only eleven percent of rows have an
 * explicit RPE, and without this inference the
 * strength analytics would be nearly empty.
 *
 * A date and time identifies a session, not a
 * date, because a real export contains two
 * sessions on one day.
 */
public final class HevyCsvAdapter
        implements DataSource<WorkoutSession> {

    /** The RPE a set taken to failure implies. */
    public static final double FAILURE_RPE = 10.0;

    private static final DateTimeFormatter
        TIMESTAMP = DateTimeFormatter.ofPattern(
            "d MMM yyyy, HH:mm", Locale.ENGLISH);

    private static final List<String> REQUIRED =
        List.of("start_time", "exercise_title",
                "set_type", "weight_lbs",
                "reps");

    private final ExerciseCatalog catalog;
    private final List<String> skipped =
            new ArrayList<>();

    public HevyCsvAdapter(
            ExerciseCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "Hevy";
    }

    @Override
    public boolean canRead(Path file) {
        try {
            return CsvFileReader.readHeader(file)
                    .containsAll(REQUIRED);
        } catch (CsvFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> skipped() {
        return List.copyOf(skipped);
    }

    @Override
    public List<WorkoutSession> read(Path file) {
        skipped.clear();
        List<Map<String, String>> rows =
                CsvFileReader.read(file);
        if (rows.isEmpty()) {
            return List.of();
        }

        // Keyed by the exact start time, so two
        // sessions on one day stay separate.
        Map<String, Builder> sessions =
                new LinkedHashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int lineNumber = i + 2;

            String type = row.getOrDefault(
                    "set_type", "normal");
            if ("warmup".equalsIgnoreCase(type)) {
                continue;
            }

            Optional<SetEntry> set =
                    setFrom(row, type,
                            lineNumber);
            if (set.isEmpty()) {
                continue;
            }

            String startTime = row.getOrDefault(
                    "start_time", "");
            Optional<LocalDate> date =
                    dateFrom(startTime,
                            lineNumber);
            if (date.isEmpty()) {
                continue;
            }

            sessions.computeIfAbsent(startTime,
                key -> new Builder(date.get(),
                    key,
                    row.getOrDefault("title",
                            null)))
                .sets.add(set.get());
        }

        List<WorkoutSession> built =
                new ArrayList<>();
        for (Builder builder
                : sessions.values()) {
            if (!builder.sets.isEmpty()) {
                built.add(builder.build());
            }
        }
        return List.copyOf(built);
    }

    private Optional<SetEntry> setFrom(
            Map<String, String> row,
            String setType,
            int lineNumber) {
        String name = row.getOrDefault(
                "exercise_title", "").trim();
        if (name.isEmpty()) {
            skipped.add("line " + lineNumber
                    + ": no exercise name");
            return Optional.empty();
        }

        String repsText =
                row.getOrDefault("reps", "");
        String weightText =
                row.getOrDefault("weight_lbs",
                        "");
        if (repsText.isBlank()) {
            skipped.add("line " + lineNumber
                    + ": " + name
                    + " has no reps");
            return Optional.empty();
        }

        int reps;
        double pounds;
        try {
            reps = (int) Double.parseDouble(
                    repsText);
            pounds = weightText.isBlank()
                    ? 0
                    : Double.parseDouble(
                            weightText);
        } catch (NumberFormatException e) {
            skipped.add("line " + lineNumber
                    + ": " + name
                    + " has an unreadable"
                    + " weight or rep count");
            return Optional.empty();
        }

        if (reps <= 0) {
            skipped.add("line " + lineNumber
                    + ": " + name
                    + " records no completed"
                    + " reps");
            return Optional.empty();
        }

        Exercise exercise = catalog.byName(name)
                .orElseGet(() -> catalog.add(
                    ExerciseNaming.fromHevy(
                            name)));

        double kilograms =
                WeightUnit.LB.toKg(pounds);

        if ("failure".equalsIgnoreCase(setType)) {
            return Optional.of(SetEntry.of(
                exercise, kilograms, reps,
                FAILURE_RPE));
        }

        String rpeText =
                row.getOrDefault("rpe", "");
        if (rpeText.isBlank()) {
            return Optional.of(
                SetEntry.withoutRpe(exercise,
                        kilograms, reps));
        }
        try {
            return Optional.of(SetEntry.of(
                exercise, kilograms, reps,
                Double.parseDouble(rpeText)));
        } catch (IllegalArgumentException e) {
            // Covers an unreadable number and an
            // RPE outside the range SetEntry
            // accepts. Either way the set still
            // counts toward volume.
            return Optional.of(
                SetEntry.withoutRpe(exercise,
                        kilograms, reps));
        }
    }

    private Optional<LocalDate> dateFrom(
            String timestamp, int lineNumber) {
        if (timestamp == null
                || timestamp.isBlank()) {
            skipped.add("line " + lineNumber
                    + ": no start time");
            return Optional.empty();
        }
        try {
            return Optional.of(
                LocalDate.parse(timestamp.trim(),
                        TIMESTAMP));
        } catch (DateTimeParseException e) {
            skipped.add("line " + lineNumber
                    + ": unreadable date \""
                    + timestamp + "\"");
            return Optional.empty();
        }
    }

    /** Collects the sets of one session. */
    private static final class Builder {
        private final LocalDate date;
        private final String externalId;
        private final String title;
        private final List<SetEntry> sets =
                new ArrayList<>();

        private Builder(LocalDate date,
                        String externalId,
                        String title) {
            this.date = date;
            this.externalId = externalId;
            this.title = title;
        }

        private WorkoutSession build() {
            return new WorkoutSession(0, date,
                    "hevy", externalId, title,
                    sets);
        }
    }
}
