package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * Builds a training block from a programme the
 * lifter already has, without a model.
 *
 * Two of F14's three routes. A programme given as
 * a spreadsheet, or typed a line at a time, has a
 * structure already; nothing needs to be
 * interpreted, only read. Sending either to a
 * model would be paying for reading
 * comprehension that is not required, and would
 * make a deterministic import depend on a network
 * call.
 *
 * Only the third route, a programme as prose,
 * needs the agent, which is why it is the only
 * one that does.
 */
public final class ProgrammeImportService {

    /** Fields a programme CSV cannot do without. */
    public static final List<String> REQUIRED =
            List.of("week", "exercise", "sets",
                    "reps");

    /** Fields that improve one if present. */
    public static final List<String> OPTIONAL =
            List.of("day", "weight", "rpe",
                    "focus");

    /**
     * One typed line, such as
     * "1/2 Back Squat 5x5@140 rpe8".
     */
    private static final Pattern ENTRY =
        Pattern.compile(
            "^\\s*(\\d+)\\s*/\\s*(\\d+)\\s+"
            + "(.+?)\\s+(\\d+)\\s*[xX]\\s*(\\d+)"
            + "(?:\\s*@\\s*([\\d.]+))?"
            + "(?:\\s+rpe\\s*([\\d.]+))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final ExerciseCatalog catalog;
    private final List<String> problems =
            new ArrayList<>();

    public ProgrammeImportService(
            ExerciseCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        this.catalog = catalog;
    }

    /** What could not be read, and why. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /**
     * A programme typed a line at a time.
     *
     * Each line is week, day, movement, sets by
     * reps, and optionally a weight and an RPE:
     * "1/2 Back Squat 5x5@140 rpe8". Terse on
     * purpose, because a lifter entering four
     * weeks of training will type this forty
     * times and every extra character is paid
     * forty times over.
     *
     * @param lines     the typed entries
     * @param startDate the block's first day
     */
    public Optional<TrainingBlock>
            fromManualEntry(
                List<String> lines,
                LocalDate startDate) {

        problems.clear();
        if (lines == null || lines.isEmpty()) {
            problems.add("nothing was entered");
            return Optional.empty();
        }

        Map<Integer, Map<Integer,
                List<ExercisePrescription>>>
            byWeek = new LinkedHashMap<>();
        int id = 1;
        int number = 0;

        for (String line : lines) {
            number++;
            if (line == null
                    || line.isBlank()) {
                continue;
            }
            Matcher parsed =
                    ENTRY.matcher(line);
            if (!parsed.matches()) {
                problems.add("line " + number
                    + ": \"" + line.strip()
                    + "\" is not in the form"
                    + " week/day exercise"
                    + " setsxreps@weight");
                return Optional.empty();
            }

            Optional<Exercise> exercise =
                catalog.byName(
                        parsed.group(3).trim());
            if (exercise.isEmpty()) {
                problems.add("line " + number
                    + ": there is no exercise"
                    + " called \""
                    + parsed.group(3).trim()
                    + "\"");
                return Optional.empty();
            }

            int week = Integer.parseInt(
                    parsed.group(1));
            int day = Integer.parseInt(
                    parsed.group(2));

            try {
                byWeek
                    .computeIfAbsent(week,
                        key ->
                            new LinkedHashMap<>())
                    .computeIfAbsent(day,
                        key -> new ArrayList<>())
                    .add(new ExercisePrescription(
                        id++, exercise.get(),
                        Integer.parseInt(
                            parsed.group(4)),
                        Integer.parseInt(
                            parsed.group(5)),
                        parsed.group(7) == null
                            ? null
                            : Double.parseDouble(
                                parsed.group(7)),
                        parsed.group(6) == null
                            ? 0
                            : Double.parseDouble(
                                parsed.group(6))));
            } catch (IllegalArgumentException e) {
                problems.add("line " + number
                    + ": " + e.getMessage());
                return Optional.empty();
            }
        }

        return assemble(byWeek, startDate,
                "entered by hand");
    }

    /**
     * A programme as a spreadsheet.
     *
     * The same mapping machinery as a workout
     * import, pointed at different fields,
     * because the problem is the same one: a file
     * whose columns nobody agreed on, and a
     * person who can say what they mean.
     *
     * @param file      the programme
     * @param mapping   which column means what
     * @param startDate the block's first day
     */
    public Optional<TrainingBlock> fromCsv(
            Path file,
            ColumnMapping mapping,
            LocalDate startDate) {

        problems.clear();
        if (mapping == null) {
            problems.add("no column mapping was"
                    + " given");
            return Optional.empty();
        }
        for (String field : REQUIRED) {
            if (mapping.columnFor(field)
                    .isEmpty()) {
                problems.add("no column is"
                    + " mapped to " + field);
            }
        }
        if (!problems.isEmpty()) {
            return Optional.empty();
        }

        Map<Integer, Map<Integer,
                List<ExercisePrescription>>>
            byWeek = new LinkedHashMap<>();
        int id = 1;
        int row = 1;

        for (Map<String, String> values
                : CsvFileReader.read(file)) {
            row++;

            Integer week = wholeOf(valueOf(
                values, mapping, "week"));
            if (week == null) {
                problems.add("row " + row
                    + ": the week could not be"
                    + " read");
                continue;
            }
            Integer day = wholeOf(valueOf(
                values, mapping, "day"));
            if (day == null) {
                day = 1;
            }

            String name = valueOf(values,
                    mapping, "exercise");
            Optional<Exercise> exercise =
                    catalog.byName(name);
            if (exercise.isEmpty()) {
                problems.add("row " + row
                    + ": there is no exercise"
                    + " called \"" + name
                    + "\"");
                continue;
            }

            Integer sets = wholeOf(valueOf(
                values, mapping, "sets"));
            Integer reps = wholeOf(valueOf(
                values, mapping, "reps"));
            if (sets == null || reps == null
                    || sets <= 0 || reps <= 0) {
                problems.add("row " + row
                    + ": the sets or reps could"
                    + " not be read");
                continue;
            }

            Double weight = numberOf(valueOf(
                values, mapping, "weight"));
            double kg = weight == null
                    ? 0
                    : mapping.unit().toKg(
                            weight);

            byWeek
                .computeIfAbsent(week,
                    key -> new LinkedHashMap<>())
                .computeIfAbsent(day,
                    key -> new ArrayList<>())
                .add(new ExercisePrescription(
                    id++, exercise.get(), sets,
                    reps,
                    numberOf(valueOf(values,
                        mapping, "rpe")),
                    kg));
        }

        if (byWeek.isEmpty()) {
            problems.add("no usable rows were"
                    + " found");
            return Optional.empty();
        }
        return assemble(byWeek, startDate,
                "imported from a file");
    }

    /**
     * Weeks and days into a block.
     *
     * A day number becomes a date by counting
     * from the start, which assumes the lifter
     * begins on day one of week one. That is what
     * a written programme means by day two, and
     * guessing anything cleverer would be
     * inventing a schedule the programme does not
     * state.
     */
    private Optional<TrainingBlock> assemble(
            Map<Integer, Map<Integer,
                    List<ExercisePrescription>>>
                byWeek,
            LocalDate startDate,
            String splitName) {

        if (byWeek.isEmpty()) {
            problems.add("nothing usable was"
                    + " read");
            return Optional.empty();
        }
        LocalDate from = startDate == null
                ? LocalDate.now()
                : startDate;

        List<Week> weeks = new ArrayList<>();
        int sessionId = 1;

        List<Integer> weekNumbers =
                new ArrayList<>(byWeek.keySet());
        weekNumbers.sort(Integer::compareTo);

        for (int weekNumber : weekNumbers) {
            Map<Integer,
                    List<ExercisePrescription>>
                days = byWeek.get(weekNumber);
            List<Integer> dayNumbers =
                    new ArrayList<>(
                        days.keySet());
            dayNumbers.sort(Integer::compareTo);

            List<Session> sessions =
                    new ArrayList<>();
            for (int day : dayNumbers) {
                sessions.add(new Session(
                    sessionId++,
                    from.plusWeeks(
                            weekNumber - 1L)
                        .plusDays(day - 1L),
                    "day " + day,
                    days.get(day)));
            }
            weeks.add(new Week(weekNumber,
                    sessions));
        }

        try {
            return Optional.of(
                new TrainingBlock(0, from,
                    "as written", splitName,
                    BlockOrigin.ADOPTED, weeks));
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return Optional.empty();
        }
    }

    private static String valueOf(
            Map<String, String> values,
            ColumnMapping mapping,
            String field) {
        Optional<String> column =
                mapping.columnFor(field);
        if (column.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry
                : values.entrySet()) {
            if (entry.getKey().trim()
                    .equalsIgnoreCase(
                        column.get().trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Double numberOf(
            String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim()
                .replace(",", "")
                .replace("%", "")
                .toLowerCase(Locale.ROOT)
                .replace("kg", "")
                .replace("lb", "")
                .trim();
        try {
            return Double.parseDouble(cleaned);
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
