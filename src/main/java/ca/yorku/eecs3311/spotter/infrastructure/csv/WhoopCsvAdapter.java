package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoverySource;

/**
 * Reads a Whoop export.
 *
 * The file wanted is physiological_cycles.csv,
 * which carries the recovery score, heart rate
 * variability and sleep for each cycle.
 *
 * Two things a real export taught. Sleep is in
 * minutes, so it is divided by sixty. And a cycle
 * begins at sleep onset, so a nap starts a second
 * cycle and the same calendar day can appear
 * twice; in a real nine month export sixty one
 * days did. Spotter keeps one reading per day, so
 * the cycle with the longest sleep is taken,
 * which is the night rather than the nap.
 */
public final class WhoopCsvAdapter
        implements DataSource<RecoveryDay> {

    private static final String START =
            "cycle start time";
    private static final String RECOVERY =
            "recovery score %";
    private static final String HRV =
            "heart rate variability (ms)";
    private static final String ASLEEP =
            "asleep duration (min)";

    private static final int MINUTES_IN_HOUR =
            60;

    private final List<String> skipped =
            new ArrayList<>();

    @Override
    public String name() {
        return "Whoop";
    }

    @Override
    public boolean canRead(Path file) {
        try {
            List<String> header =
                CsvFileReader.readHeader(file);
            return header.contains(START)
                && (header.contains(RECOVERY)
                    || header.contains(ASLEEP));
        } catch (CsvFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> skipped() {
        return List.copyOf(skipped);
    }

    @Override
    public List<RecoveryDay> read(Path file) {
        skipped.clear();
        List<Map<String, String>> rows =
                CsvFileReader.read(file);

        // Keeps the longest sleep for each day.
        Map<LocalDate, RecoveryDay> best =
                new LinkedHashMap<>();
        Map<LocalDate, Double> bestSleep =
                new LinkedHashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int lineNumber = i + 2;

            Optional<LocalDate> date =
                dateFrom(row.get(START),
                        lineNumber);
            if (date.isEmpty()) {
                continue;
            }

            OptionalDouble recovery =
                    number(row.get(RECOVERY));
            OptionalDouble hrv =
                    number(row.get(HRV));
            OptionalDouble asleep =
                    number(row.get(ASLEEP));

            if (recovery.isEmpty()
                    && hrv.isEmpty()
                    && asleep.isEmpty()) {
                skipped.add("line " + lineNumber
                    + ": no readings, the cycle"
                    + " was probably still in"
                    + " progress");
                continue;
            }

            RecoveryDay.Builder builder =
                RecoveryDay.on(date.get(),
                    RecoverySource.WEARABLE_CSV);

            recovery.ifPresent(score ->
                builder.recoveryScore(
                        (int) Math.round(score)));
            hrv.ifPresent(builder::hrvMs);
            double hours = asleep.orElse(0)
                    / MINUTES_IN_HOUR;
            if (asleep.isPresent()) {
                builder.sleepHours(hours);
            }

            RecoveryDay day;
            try {
                day = builder.build();
            } catch (IllegalArgumentException e) {
                skipped.add("line " + lineNumber
                    + ": " + e.getMessage());
                continue;
            }

            Double previous =
                    bestSleep.get(date.get());
            if (previous == null
                    || hours > previous) {
                best.put(date.get(), day);
                bestSleep.put(date.get(), hours);
            }
        }
        return List.copyOf(
                new ArrayList<>(best.values()));
    }

    private Optional<LocalDate> dateFrom(
            String timestamp, int lineNumber) {
        if (timestamp == null
                || timestamp.isBlank()) {
            skipped.add("line " + lineNumber
                    + ": no cycle start time");
            return Optional.empty();
        }
        // "2026-10-29 03:16:55"
        String datePart =
                timestamp.trim().split(" ")[0];
        try {
            return Optional.of(
                    LocalDate.parse(datePart));
        } catch (RuntimeException e) {
            skipped.add("line " + lineNumber
                    + ": unreadable date \""
                    + timestamp + "\"");
            return Optional.empty();
        }
    }

    private static OptionalDouble number(
            String value) {
        if (value == null || value.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(
                Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
