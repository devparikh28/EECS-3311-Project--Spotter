package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .PlanAdherence;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;

import ca.yorku.eecs3311.spotter.application
        .event.SpotterEvent;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The first screen.
 *
 * Built around the question a lifter actually
 * opens the application with, which is not "what
 * are my maxima" but "what am I training today,
 * and is anything wrong". So the hero answers
 * that, in the largest type on the screen, and
 * everything else is arranged beneath it in the
 * order a person would ask: am I recovered, how
 * is the block going, where are my lifts.
 *
 * The hero is the accent colour only when there
 * is training to do. A card that is always bright
 * is decoration; one that changes is information,
 * and a lifter glancing at this from across a
 * room should be able to tell the difference
 * without reading a word.
 *
 * Every figure here is read from the analytics
 * and the adherence model rather than recomputed,
 * because a second implementation would
 * eventually disagree with the first.
 */
public final class DashboardPanel
        implements Panel {

    /** How far back flags are worth showing. */
    public static final int FLAG_WEEKS = 1;

    private static final DateTimeFormatter DAY =
        DateTimeFormatter.ofPattern(
            "EEEE d MMMM", Locale.ENGLISH);

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);

    public DashboardPanel(GuiContext context) {
        this.context = context;
    }

    /**
     * Everything, because the dashboard is a
     * summary of every other screen. A panel
     * that shows a figure from each repository
     * falls behind on any change.
     */
    @Override
    public java.util.Set<SpotterEvent>
            changedBy() {
        return java.util.EnumSet.allOf(
                SpotterEvent.class);
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public String description() {
        return "Where you stand today.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();

        if (!context.controller()
                .hasTrainingHistory()) {
            root.getChildren().addAll(
                Cards.hero(false,
                    "NOTHING LOGGED YET",
                    "Let's get some training in",
                    "Spotter works from what you"
                    + " have actually lifted."
                    + " Open Import in the menu"
                    + " on the left to bring in"
                    + " an export from your"
                    + " tracking app, or load"
                    + " the sample lifter to look"
                    + " around first."),
                Cards.emptyState(
                    "Once there is history,"
                    + " this screen shows"
                    + " today's session, your"
                    + " recovery, how the block"
                    + " is going and where your"
                    + " lifts are.",
                    "spotter seed"));
            return;
        }

        root.getChildren().add(hero());
        root.getChildren().add(
                Cards.sectionHeader(
                    "YOUR LIFTS"));
        root.getChildren().add(strengthRow());

        Node progress = blockProgress();
        if (progress != null) {
            root.getChildren().addAll(
                Cards.sectionHeader(
                    "THIS BLOCK"),
                progress);
        }

        root.getChildren().addAll(
            Cards.sectionHeader("YOU"),
            Cards.row(recoveryCard(),
                    profileCard()));
    }

    /**
     * Today's training, or the reason there is
     * none, in the largest type on the screen.
     */
    private Node hero() {
        TrainingBlock block = context.blocks()
                .active().orElse(null);

        if (block == null) {
            return Cards.hero(false,
                LocalDate.now().format(DAY)
                    .toUpperCase(Locale.ENGLISH),
                "No block yet",
                "Generate one on the Plan"
                + " screen, or adopt a programme"
                + " you already follow. Nothing"
                + " is saved until you keep it.");
        }

        Session today = sessionOn(block,
                LocalDate.now());
        if (today != null) {
            return trainingHero(today);
        }

        Session next = nextSessionAfter(block,
                LocalDate.now());
        if (next == null) {
            return Cards.hero(false,
                LocalDate.now().format(DAY)
                    .toUpperCase(Locale.ENGLISH),
                "Block complete",
                "Every session in this block has"
                + " come and gone. Review it on"
                + " the Plan screen and build"
                + " the next one.");
        }

        long days = LocalDate.now()
            .datesUntil(next.date()).count();
        return Cards.hero(false,
            "REST DAY",
            days == 1
                ? "Back at it tomorrow"
                : "Next session in " + days
                    + " days",
            next.focus()
                .map(focus -> focus
                    + ", " + next.date()
                        .format(DAY))
                .orElse(next.date().format(DAY))
            + ". Rest is part of the plan.");
    }

    /** The bright one: there is training to do. */
    private Node trainingHero(Session today) {
        int movements =
                today.prescriptions().size();

        VBox left = new VBox(2);
        left.getChildren().addAll(
            Cards.styled(LocalDate.now()
                .format(DAY)
                .toUpperCase(Locale.ENGLISH),
                "hero-eyebrow"),
            Cards.styled(
                headline(today.focus()
                        .orElse(null)),
                "hero-figure"),
            Cards.styled(String.format(
                "%d movement%s to get through.",
                movements,
                movements == 1 ? "" : "s"),
                "hero-caption"));

        VBox list = new VBox(1);
        for (ExercisePrescription p
                : today.prescriptions()) {
            list.getChildren().add(
                Cards.styled(String.format(
                    "%-26s %d x %d @ %.1f kg",
                    p.exercise().name(),
                    p.sets(), p.reps(),
                    p.loadKg()), "mono"));
        }
        VBox.setMargin(list,
            new javafx.geometry.Insets(
                10, 0, 0, 0));
        left.getChildren().add(list);

        today.adjustmentNote().ifPresent(note ->
            left.getChildren().add(
                Cards.styled(note,
                        "hero-caption")));

        VBox right = new VBox(2);
        right.setAlignment(
                javafx.geometry.Pos.TOP_RIGHT);
        right.getChildren().addAll(
            Cards.styled("PLANNED WORK",
                    "hero-eyebrow"),
            Cards.styled(String.format("%.0f kg",
                today.plannedTonnage()),
                "hero-secondary"));

        readinessBadge().ifPresent(badge -> {
            VBox.setMargin(badge,
                new javafx.geometry.Insets(
                    12, 0, 0, 0));
            right.getChildren().add(badge);
        });

        VBox hero = Cards.hero(true, null, null,
            null, Cards.split(left, right));
        return hero;
    }

    /**
     * A single word focus reads as a fragment on
     * its own, so it gets the noun back.
     */
    private static String headline(
            String focus) {
        if (focus == null || focus.isBlank()) {
            return "Training today";
        }
        String trimmed = focus.trim();
        String capitalised =
            Character.toUpperCase(
                trimmed.charAt(0))
            + trimmed.substring(1);
        return trimmed.contains(" ")
            ? capitalised
            : capitalised + " day";
    }

    /**
     * The most urgent thing a lifter can know
     * before training, so it goes in the hero
     * rather than in a card below the fold.
     */
    private Optional<javafx.scene.control.Label>
            readinessBadge() {
        List<RecoveryFlag> flags = context
            .controller().recoveryFlags(
                LocalDate.now().minusWeeks(
                        FLAG_WEEKS));
        if (flags.isEmpty()) {
            return Optional.of(Cards.badge(
                "RECOVERED", "good"));
        }
        boolean significant = flags.stream()
            .anyMatch(
                RecoveryFlag::isSignificant);
        return Optional.of(Cards.badge(
            significant ? "EASE OFF" : "WATCH IT",
            significant ? "bad" : "warn"));
    }

    private static Session sessionOn(
            TrainingBlock block,
            LocalDate date) {
        return block.allSessions().stream()
            .filter(each -> each.date()
                    .equals(date))
            .findFirst()
            .orElse(null);
    }

    private static Session nextSessionAfter(
            TrainingBlock block,
            LocalDate date) {
        return block.allSessions().stream()
            .filter(each -> each.date()
                    .isAfter(date))
            .min((one, other) -> one.date()
                    .compareTo(other.date()))
            .orElse(null);
    }

    /** The three competition lifts, side by side. */
    private Node strengthRow() {
        Map<Lift, Double> current =
                context.controller()
                    .currentE1RMs();
        return Cards.row(
            statFor(Lift.SQUAT, current),
            statFor(Lift.BENCH, current),
            statFor(Lift.DEADLIFT, current));
    }

    private VBox statFor(
            Lift lift,
            Map<Lift, Double> current) {

        String heading = lift.name();
        Double e1rm = current.get(lift);
        if (e1rm == null) {
            return Cards.stat(heading,
                "no data", null,
                Cards.Direction.FLAT,
                "nothing logged for this lift");
        }

        Optional<Double> trend = context
                .controller().trendFor(lift);
        if (trend.isEmpty()) {
            return Cards.stat(heading,
                String.format("%.1f kg", e1rm),
                null, Cards.Direction.FLAT,
                "one session only, so no trend"
                + " yet");
        }

        double moved = trend.get();
        Cards.Direction direction =
            Math.abs(moved) < 0.5
                ? Cards.Direction.FLAT
                : moved > 0
                    ? Cards.Direction.UP
                    : Cards.Direction.DOWN;

        return Cards.stat(heading,
            String.format("%.1f kg", e1rm),
            String.format("%+.1f kg", moved),
            direction,
            "estimated from your heaviest"
            + " usable set");
    }

    /**
     * How the block is actually going, which is
     * the figure that decides whether the next
     * one should add load.
     */
    private Node blockProgress() {
        TrainingBlock block = context.blocks()
                .active().orElse(null);
        if (block == null) {
            return null;
        }

        PlanAdherence adherence =
            PlanAdherence.of(block,
                context.controller().workouts()
                    .findAll(),
                LocalDate.now());

        if (adherence.isEmpty()) {
            return Cards.card("THIS BLOCK",
                Cards.note("No session has come"
                    + " due yet, so there is"
                    + " nothing to judge."));
        }

        VBox sessions = Cards.meter(
            "SESSIONS TRAINED",
            adherence.completed() + " of "
                + adherence.planned(),
            adherence.sessionRate());

        VBox work = Cards.meter("WORK DONE",
            String.format("%.0f percent",
                adherence.tonnageRate() * 100),
            adherence.tonnageRate());

        VBox card = Cards.card(null,
                Cards.row(sessions, work));

        List<Lift> behind =
                adherence.liftsShortOfPlan();
        card.getChildren().add(behind.isEmpty()
            ? Cards.styled("Every lift is on"
                + " plan", "good")
            : Cards.note("Short of plan: "
                + behind + ". Loads for these"
                + " will not be allowed to"
                + " advance."));
        return card;
    }

    /** Anything recovery flagged recently. */
    private Node recoveryCard() {
        List<RecoveryFlag> flags = context
            .controller().recoveryFlags(
                LocalDate.now().minusWeeks(
                        FLAG_WEEKS));

        if (flags.isEmpty()) {
            VBox card = Cards.card("RECOVERY");
            card.getChildren().addAll(
                new HBox(8, Cards.badge(
                    "CLEAR", "good")),
                Cards.note("Nothing flagged this"
                    + " week, so the plan stands"
                    + " as written."));
            return card;
        }

        RecoveryFlag worst = flags.stream()
            .filter(RecoveryFlag::isSignificant)
            .findFirst()
            .orElse(flags.get(flags.size() - 1));

        VBox card = Cards.card("RECOVERY");
        card.getChildren().add(new HBox(8,
            Cards.badge(worst.isSignificant()
                ? "EASE OFF" : "WATCH IT",
                worst.isSignificant()
                    ? "bad" : "warn")));
        for (RecoveryFlag flag : flags) {
            card.getChildren().add(
                Cards.note(flag.date() + "  "
                    + flag.summary()));
        }
        card.getChildren().add(
            Cards.note("The Today screen can"
                + " ease the session off."));
        return card;
    }

    /** Who Spotter thinks it is coaching. */
    private Node profileCard() {
        LifterProfile profile =
                context.profile().orElse(null);
        if (profile == null) {
            VBox card = Cards.card("LIFTER");
            card.getChildren().addAll(
                new HBox(8, Cards.badge(
                    "NOT SET UP", "quiet")),
                Cards.note("Set a profile up on"
                    + " the Profile screen so"
                    + " plans are built for you"
                    + " rather than for"
                    + " nobody."));
            return card;
        }
        VBox card = Cards.card("LIFTER");
        card.getChildren().addAll(
            Cards.field("goal",
                readable(profile.goal().name())),
            Cards.field("experience",
                readable(profile.experience()
                    .name())),
            Cards.field("trains",
                profile.trainingDaysPerWeek()
                    + " days a week"),
            Cards.field("bodyweight",
                String.format("%.1f kg",
                    profile.bodyweightKg())));
        return card;
    }

    /** MEET_PREP reads badly on a screen. */
    private static String readable(String name) {
        return name.toLowerCase(Locale.ENGLISH)
                .replace('_', ' ');
    }
}
