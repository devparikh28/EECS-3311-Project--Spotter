package ca.yorku.eecs3311.spotter.agent.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent.Tool;
import ca.yorku.eecs3311.spotter.agent
        .ToolSchema;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;

/**
 * Lets an agent see how the lifter has been
 * recovering.
 *
 * The readings are reported as they were
 * recorded, and the verdict on each day comes
 * from the same rule engine the rest of Spotter
 * uses. The model is told which days were
 * flagged rather than being asked to decide what
 * counts as a bad night, because that threshold
 * is a decision the design already made and
 * should not drift between replies.
 */
public final class RecoveryLookupTool
        implements Tool {

    /** Most days reported in one reply. */
    public static final int MAX_DAYS = 14;

    private final List<RecoveryDay> days;
    private final RecoveryRuleEngine rules;

    public RecoveryLookupTool(
            List<RecoveryDay> days,
            RecoveryRuleEngine rules) {
        this.days = days == null
                ? List.of()
                : List.copyOf(days);
        this.rules = rules == null
                ? new RecoveryRuleEngine()
                : rules;
    }

    @Override
    public String name() {
        return "recovery_readings";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(),
            "Recent sleep, heart rate"
            + " variability, recovery score and"
            + " reported soreness, with Spotter's"
            + " own verdict on each day. Call"
            + " this before changing a session."
            + " Do not decide for yourself what"
            + " counts as poor recovery.",
            Map.of("days",
                "optional: how many recent days"
                + " to report, up to "
                + MAX_DAYS));
    }

    @Override
    public String execute(
            Map<String, String> arguments) {
        if (days.isEmpty()) {
            return "No recovery readings have"
                + " been recorded.";
        }

        int wanted = requestedDays(arguments);

        List<RecoveryDay> recent =
                new ArrayList<>(days);
        recent.sort(Comparator.comparing(
                RecoveryDay::date).reversed());
        if (recent.size() > wanted) {
            recent = recent.subList(0, wanted);
        }

        StringBuilder report =
                new StringBuilder();
        for (RecoveryDay day : recent) {
            report.append(describe(day))
                  .append('\n');
        }
        return report.toString().strip();
    }

    private int requestedDays(
            Map<String, String> arguments) {
        String asked = arguments == null
                ? null
                : arguments.get("days");
        if (asked == null || asked.isBlank()) {
            return MAX_DAYS;
        }
        try {
            int value = Integer.parseInt(
                    asked.trim());
            return Math.min(MAX_DAYS,
                    Math.max(1, value));
        } catch (IllegalArgumentException e) {
            return MAX_DAYS;
        }
    }

    private String describe(RecoveryDay day) {
        StringBuilder line =
                new StringBuilder();
        LocalDate date = day.date();
        line.append(date).append(": ");

        List<String> readings =
                new ArrayList<>();
        day.recoveryScore().ifPresent(score ->
            readings.add("recovery " + score
                    + " percent"));
        day.hrvMs().ifPresent(hrv ->
            readings.add(String.format(
                    "HRV %.0f ms", hrv)));
        day.sleepHours().ifPresent(hours ->
            readings.add(String.format(
                    "slept %.1f hours",
                    hours)));
        day.soreness().ifPresent(soreness ->
            readings.add("soreness " + soreness
                    + " of 5"));
        day.energy().ifPresent(energy ->
            readings.add("energy " + energy
                    + " of 5"));

        if (readings.isEmpty()) {
            line.append("nothing recorded");
        } else {
            line.append(String.join(", ",
                    readings));
        }

        Optional<RecoveryFlag> flag =
                rules.evaluate(day);
        if (flag.isPresent()) {
            line.append(" [flagged, ")
                .append(flag.get()
                    .isSignificant()
                        ? "significant"
                        : "mild")
                .append(": ")
                .append(flag.get().summary())
                .append(']');
        } else {
            line.append(" [not flagged]");
        }
        return line.toString();
    }
}
