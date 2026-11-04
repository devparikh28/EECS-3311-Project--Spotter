package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import ca.yorku.eecs3311.spotter.domain
        .RecoverySource;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F03: recovery reported by the lifter rather
 * than by a wearable.
 *
 * The rule engine has always read soreness and
 * energy, and nothing could produce them. A
 * lifter without a Whoop could log months of
 * training and never have a session questioned,
 * which made the recovery feature conditional on
 * owning a particular device.
 *
 * Deliberately a few numbers rather than a
 * questionnaire. Anything longer gets skipped on
 * exactly the mornings it matters.
 */
@Command(
    name = "checkin",
    description =
        "Record how you feel today, for days"
        + " with no wearable data.")
public final class CheckInCommand
        implements Callable<Integer> {

    /** Returned when nothing was reported. */
    public static final int EXIT_NOTHING = 3;

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--soreness"},
        paramLabel = "1-5",
        description =
            "How sore you are, 1 fresh to 5"
            + " wrecked.")
    Integer soreness;

    @Option(
        names = {"--energy"},
        paramLabel = "1-5",
        description =
            "How much energy you have, 1 flat"
            + " to 5 excellent.")
    Integer energy;

    @Option(
        names = {"--sleep"},
        paramLabel = "HOURS",
        description = "Hours slept last night.")
    Double sleep;

    @Option(
        names = {"--date"},
        paramLabel = "DATE",
        description =
            "The day being reported, as"
            + " YYYY-MM-DD. Default today.")
    LocalDate date;

    @Option(
        names = {"--json"},
        description =
            "Print the result as JSON instead of"
            + " prose, for a harness rather than"
            + " a person.")
    boolean json;

    @Option(
        names = {"--env"},
        paramLabel = "FILE",
        description = "Configuration file.")
    Path envFile = Path.of(".env");

    /**
     * What was recorded, and what it triggered.
     *
     * The flag is the part a harness cares
     * about, because it is what decides whether
     * `spotter adjust` is allowed to change
     * anything.
     */
    private static java.util.Map<String, Object>
            recorded(RecoveryDay day,
                     RecoveryFlag flag) {
        java.util.Map<String, Object> described =
                new java.util.LinkedHashMap<>();
        described.put("date",
                day.date().toString());
        described.put("source",
                day.source().name());
        described.put("soreness",
            day.soreness().isPresent()
                ? day.soreness().getAsInt()
                : null);
        described.put("energy",
            day.energy().isPresent()
                ? day.energy().getAsInt()
                : null);
        described.put("sleepHours",
            day.sleepHours().isPresent()
                ? day.sleepHours().getAsDouble()
                : null);
        described.put("flagged", flag != null);
        if (flag != null) {
            described.put("significant",
                    flag.isSignificant());
            described.put("summary",
                    flag.summary());
        }
        return described;
    }

    @Override
    public Integer call() {
        SpotterConfig config =
                SpotterConfig.load(envFile);
        PrintWriter out =
                spec.commandLine().getOut();

        if (soreness == null && energy == null
                && sleep == null) {
            if (json) {
                JsonOutput.refusal(out,
                    "checkin",
                    "nothing was reported");
                return EXIT_NOTHING;
            }
            out.println("Nothing was reported.");
            out.println("  Try: spotter checkin"
                + " --soreness 4 --energy 2"
                + " --sleep 5.5");
            out.flush();
            return EXIT_NOTHING;
        }

        LocalDate when = date == null
                ? LocalDate.now()
                : date;

        RecoveryDay.Builder builder =
            RecoveryDay.on(when,
                RecoverySource.MANUAL_CHECK_IN);
        if (soreness != null) {
            builder.soreness(soreness);
        }
        if (energy != null) {
            builder.energy(energy);
        }
        if (sleep != null) {
            builder.sleepHours(sleep);
        }

        RecoveryDay day;
        try {
            day = builder.build();
        } catch (IllegalArgumentException e) {
            if (json) {
                JsonOutput.refusal(out,
                    "checkin",
                    e.getMessage());
                return EXIT_NOTHING;
            }
            out.println("That cannot be"
                    + " recorded.");
            out.println("  " + e.getMessage());
            out.flush();
            return EXIT_NOTHING;
        }

        try (CoachController controller =
                new CoachController(config)) {

            controller.recovery().save(day);

            RecoveryFlag flagged =
                new RecoveryRuleEngine()
                    .evaluate(day)
                    .orElse(null);
            if (json) {
                JsonOutput.write(out, "checkin",
                    true, recorded(day,
                            flagged));
                return 0;
            }

            out.printf("Recorded for %s.%n",
                    when);
            out.println();
            day.soreness().ifPresent(value ->
                out.printf(
                    "  soreness   %d of 5%n",
                    value));
            day.energy().ifPresent(value ->
                out.printf(
                    "  energy     %d of 5%n",
                    value));
            day.sleepHours().ifPresent(value ->
                out.printf(
                    "  slept      %.1f hours%n",
                    value));
            out.println();

            RecoveryFlag flag = flagged;
            if (flag == null) {
                out.println("  Nothing here is"
                    + " worth easing off for."
                    + " Train as written.");
            } else {
                out.printf(
                    "  Flagged as %s: %s%n",
                    flag.isSignificant()
                        ? "significant"
                        : "mild",
                    flag.summary());
                out.println("  Run: spotter"
                        + " adjust");
            }
            out.flush();
        }
        return 0;
    }
}
