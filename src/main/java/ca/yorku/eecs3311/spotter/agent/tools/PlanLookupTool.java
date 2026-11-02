package ca.yorku.eecs3311.spotter.agent.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.yorku.eecs3311.spotter.agent.Tool;
import ca.yorku.eecs3311.spotter.agent
        .ToolSchema;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;

/**
 * Lets an agent read the plan the lifter is
 * actually on.
 *
 * Without this a chat coach answers "what am I
 * doing today" by guessing, which is the single
 * most obvious way for it to be confidently
 * wrong. The sessions are reported as written,
 * including any note saying a session was
 * reduced, because a lifter asking about today
 * deserves to be told it was eased off and why.
 */
public final class PlanLookupTool
        implements Tool {

    /** Most sessions reported in one reply. */
    public static final int MAX_SESSIONS = 14;

    private final TrainingBlock block;
    private final LocalDate today;

    public PlanLookupTool(TrainingBlock block,
                          LocalDate today) {
        this.block = block;
        this.today = today == null
                ? LocalDate.now()
                : today;
    }

    @Override
    public String name() {
        return "training_plan";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(),
            "The block the lifter is currently"
            + " training, session by session."
            + " Call this before saying anything"
            + " about what they are doing today"
            + " or this week. Never guess at a"
            + " session.",
            Map.of("when",
                "today, week, or all; defaults"
                + " to week"));
    }

    @Override
    public String execute(
            Map<String, String> arguments) {
        if (block == null) {
            return "No block is being trained."
                + " Nothing is planned.";
        }

        String when = arguments == null
                ? null
                : arguments.get("when");
        String wanted = when == null
                || when.isBlank()
                    ? "week"
                    : when.trim().toLowerCase(
                        java.util.Locale.ROOT);

        List<Session> sessions =
                select(wanted);
        if (sessions.isEmpty()) {
            return wanted.equals("today")
                ? "Nothing is scheduled for "
                  + today + "."
                : "No sessions fall in that"
                  + " period.";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Block started "
            + block.startDate() + ", "
            + block.weekCount() + " weeks, "
            + block.progression()
                .orElse("unspecified")
            + " progression.");

        for (Session session : sessions) {
            lines.add(describe(session));
        }
        return String.join("\n", lines);
    }

    private List<Session> select(
            String wanted) {
        List<Session> chosen =
                new ArrayList<>();
        for (Session session
                : block.allSessions()) {
            if (matches(wanted, session)) {
                chosen.add(session);
            }
            if (chosen.size() >= MAX_SESSIONS) {
                break;
            }
        }
        return chosen;
    }

    private boolean matches(String wanted,
                            Session session) {
        return switch (wanted) {
            case "today" -> session.date()
                    .equals(today);
            case "all" -> true;
            default -> !session.date()
                    .isBefore(today)
                && !session.date().isAfter(
                    today.plusDays(6));
        };
    }

    private static String describe(
            Session session) {
        StringBuilder line =
                new StringBuilder();
        line.append(session.date())
            .append(" ")
            .append(session.focus()
                .orElse("training"))
            .append(": ");

        List<String> parts =
                new ArrayList<>();
        for (ExercisePrescription p
                : session.prescriptions()) {
            parts.add(String.format(
                "%s %d x %d at %.1f kg",
                p.exercise().name(), p.sets(),
                p.reps(), p.loadKg()));
        }
        line.append(String.join("; ", parts));

        session.adjustmentNote().ifPresent(note ->
            line.append(" [adjusted: ")
                .append(note).append("]"));
        return line.toString();
    }
}
