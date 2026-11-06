package ca.yorku.eecs3311.spotter
        .application.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport.Section;
import ca.yorku.eecs3311.spotter.domain
        .DailyIntake;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .MacroTarget;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .PlanAdherence;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .BlockRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .NutritionRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .ProfileRepository;

/**
 * Gathers a lifter's numbers into a report.
 *
 * Nothing is computed here that the application
 * does not already compute for a screen: strength
 * comes from the analytics, the block from
 * PlanAdherence, recovery from the rule engine's
 * flags. A report that did its own arithmetic
 * would be a second opinion, and a document handed
 * to a coach is the worst place for two figures
 * for the same lift.
 *
 * A section with nothing in it says so instead of
 * being left out, because a report that silently
 * omits recovery reads as though recovery was fine.
 */
public final class ReportBuilder {

    /** Days of recovery and food the report covers. */
    public static final int DAYS = 14;

    private final CoachController controller;
    private final ProfileRepository profiles;
    private final BlockRepository blocks;
    private final NutritionRepository nutrition;

    public ReportBuilder(
            CoachController controller,
            ProfileRepository profiles,
            BlockRepository blocks,
            NutritionRepository nutrition) {
        this.controller = controller;
        this.profiles = profiles;
        this.blocks = blocks;
        this.nutrition = nutrition;
    }

    public TrainingReport build(LocalDate today) {
        List<Section> sections =
                new ArrayList<>();
        Optional<LifterProfile> profile =
                profiles.current();

        sections.add(lifter(profile));
        sections.add(strength());
        sections.add(block(today));
        sections.add(recovery(today));
        sections.add(food(today, profile));

        return new TrainingReport(
            "Spotter training report", today,
            sections);
    }

    private Section lifter(
            Optional<LifterProfile> found) {
        if (found.isEmpty()) {
            return new Section("Lifter",
                List.of("No profile has been"
                    + " saved yet."),
                List.of(), List.of());
        }
        LifterProfile p = found.get();
        List<List<String>> rows =
                new ArrayList<>();
        rows.add(List.of("Goal",
                readable(p.goal().name())));
        rows.add(List.of("Experience",
                readable(p.experience().name())));
        rows.add(List.of("Bodyweight", String
            .format(Locale.ROOT, "%.1f kg",
                p.bodyweightKg())));
        rows.add(List.of("Training days",
            p.trainingDaysPerWeek()
                + " a week"));
        p.meetDate().ifPresent(date ->
            rows.add(List.of("Meet",
                    date.toString())));
        rows.add(List.of("Limitations",
            p.limitations().isEmpty()
                ? "none"
                : String.valueOf(
                    p.limitations().size())));
        return new Section("Lifter", List.of(),
            List.of("Item", "Value"), rows);
    }

    private Section strength() {
        Map<Lift, Double> current =
                controller.currentE1RMs();
        Map<Lift, Double> best =
                controller.allTimeE1RMs();

        List<List<String>> rows =
                new ArrayList<>();
        for (Lift lift : Lift.values()) {
            Double now = current.get(lift);
            Double ever = best.get(lift);
            if (now == null && ever == null) {
                continue;
            }
            String trend = controller
                .trendFor(lift)
                .map(moved -> String.format(
                    Locale.ROOT, "%+.1f kg",
                    moved))
                .orElse("no trend yet");
            rows.add(List.of(
                readable(lift.name()),
                kg(now), kg(ever), trend));
        }
        if (rows.isEmpty()) {
            return new Section(
                "Strength",
                List.of("Nothing has been"
                    + " logged yet."),
                List.of(), List.of());
        }
        return new Section("Strength",
            List.of("Estimated one rep maxima."
                + " Current uses the last "
                + CoachController.RECENT_WEEKS
                + " weeks."),
            List.of("Lift", "Current",
                "Best ever", "Trend"),
            rows);
    }

    private Section block(LocalDate today) {
        TrainingBlock active =
                blocks.active().orElse(null);
        if (active == null) {
            return new Section("Training block",
                List.of("No block is active."),
                List.of(), List.of());
        }
        PlanAdherence adherence =
            PlanAdherence.of(active,
                controller.workouts().findAll(),
                today);

        List<String> lines = new ArrayList<>();
        lines.add(active.weekCount()
            + " weeks starting "
            + active.startDate() + ".");
        lines.add(adherence.describe());

        List<List<String>> rows =
                new ArrayList<>();
        rows.add(List.of("Sessions done",
            adherence.completed() + " of "
                + adherence.planned()));
        rows.add(List.of("Sessions missed",
            String.valueOf(
                adherence.missed())));
        rows.add(List.of("Planned tonnage",
            String.format(Locale.ROOT,
                "%.0f kg",
                adherence.plannedTonnage())));
        rows.add(List.of("Logged tonnage",
            String.format(Locale.ROOT,
                "%.0f kg",
                adherence.actualTonnage())));
        for (Lift lift : adherence
                .liftsShortOfPlan()) {
            rows.add(List.of(
                readable(lift.name())
                    + " top load",
                "short of plan"));
        }
        return new Section("Training block",
            lines, List.of("Measure", "Value"),
            rows);
    }

    private Section recovery(LocalDate today) {
        LocalDate since = today.minusDays(DAYS);
        List<RecoveryDay> days = controller
            .recovery().findSince(since);
        List<RecoveryFlag> flags = controller
                .recoveryFlags(since);

        if (days.isEmpty()) {
            return new Section("Recovery",
                List.of("No recovery readings in"
                    + " the last " + DAYS
                    + " days."),
                List.of(), List.of());
        }
        List<List<String>> rows =
                new ArrayList<>();
        for (RecoveryDay day : days) {
            rows.add(List.of(
                day.date().toString(),
                day.recoveryScore().isPresent()
                    ? day.recoveryScore()
                        .getAsInt() + "%"
                    : "-",
                day.sleepHours().isPresent()
                    ? String.format(Locale.ROOT,
                        "%.1f h",
                        day.sleepHours()
                            .getAsDouble())
                    : "-",
                day.hrvMs().isPresent()
                    ? String.format(Locale.ROOT,
                        "%.0f ms",
                        day.hrvMs()
                            .getAsDouble())
                    : "-"));
        }
        return new Section("Recovery",
            List.of(flags.isEmpty()
                ? "Nothing was flagged."
                : flags.size() + " day"
                    + (flags.size() == 1
                        ? " was" : "s were")
                    + " flagged by the recovery"
                    + " rules."),
            List.of("Date", "Score", "Sleep",
                "HRV"),
            rows);
    }

    private Section food(
            LocalDate today,
            Optional<LifterProfile> profile) {
        LocalDate since = today.minusDays(DAYS);
        List<DailyIntake> logged =
                nutrition.findSince(since);
        Optional<MacroTarget> target =
            profile.flatMap(p ->
                nutrition.targetFor(p.id()));

        if (logged.isEmpty()) {
            return new Section("Nutrition",
                List.of("No food was logged in"
                    + " the last " + DAYS
                    + " days."),
                List.of(), List.of());
        }

        List<List<String>> rows =
                new ArrayList<>();
        MacroTotals sum = MacroTotals.ZERO;
        for (DailyIntake day : logged) {
            MacroTotals t = day.totals();
            sum = sum.plus(t);
            rows.add(List.of(
                day.date().toString(),
                String.format(Locale.ROOT,
                    "%.0f", t.calories()),
                String.format(Locale.ROOT,
                    "%.0f g", t.proteinG()),
                String.format(Locale.ROOT,
                    "%.0f g", t.carbsG()),
                String.format(Locale.ROOT,
                    "%.0f g", t.fatG())));
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
            "Average over %d logged days: %.0f"
            + " calories, %.0f g protein.",
            logged.size(),
            sum.calories() / logged.size(),
            sum.proteinG() / logged.size()));
        target.ifPresent(t -> lines.add(
            String.format(Locale.ROOT,
                "Target: %d calories, %.0f g"
                + " protein, %.0f g carbs,"
                + " %.0f g fat.",
                t.calories(), t.proteinG(),
                t.carbsG(), t.fatG())));
        return new Section("Nutrition", lines,
            List.of("Date", "Calories",
                "Protein", "Carbs", "Fat"),
            rows);
    }

    private static String kg(Double value) {
        return value == null
            ? "-"
            : String.format(Locale.ROOT,
                "%.1f kg", value);
    }

    private static String readable(String name) {
        String spaced = name.toLowerCase(
            Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(
            spaced.charAt(0))
            + spaced.substring(1);
    }
}
