package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind
        .ObjectMapper;
import com.fasterxml.jackson.databind
        .ObjectWriter;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .AgentTrace;
import ca.yorku.eecs3311.spotter.agent
        .TokenUsage;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .MealSuggestion;
import ca.yorku.eecs3311.spotter.domain
        .MeetAttempts;
import ca.yorku.eecs3311.spotter.domain
        .PlanAdherence;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;
import ca.yorku.eecs3311.spotter.domain
        .TestDayPlan;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;

/**
 * Every command's answer in one shape.
 *
 * Stage 3 runs the application from a harness
 * rather than by reading a terminal, so each
 * command that can be asked a question has to be
 * able to answer in something a program can
 * parse. The alternative was scraping the human
 * output, which would make every improvement to
 * the wording a breaking change.
 *
 * The shape is the same for every command:
 *
 * <pre>
 * {
 *   "command": "plan",
 *   "ok": true,
 *   "value": { ... },
 *   "failure": "why not, when ok is false",
 *   "trace": { "modelCalls": 2, ... }
 * }
 * </pre>
 *
 * A refusal is a successful run of the program
 * that produced no plan, so `ok` is false and the
 * exit code is the command's own. A harness reads
 * `ok`, not the words.
 *
 * `value` may be present alongside a failure, and
 * that is the point rather than an oversight.
 * When the coach refuses to explain a set of
 * attempts, the attempts themselves were
 * calculated rather than generated and still
 * stand, so the field a harness wants is still
 * there with `ok` false beside it.
 *
 * Domain objects are mapped here by hand rather
 * than handed to Jackson. They carry Optionals
 * and no annotations, and more to the point the
 * wire shape is a contract the harness depends
 * on: it should change when someone decides to
 * change it, not when someone renames a field.
 */
public final class JsonOutput {

    private static final ObjectWriter WRITER =
        new ObjectMapper().writerWithDefaultPrettyPrinter();

    private JsonOutput() {
    }

    /** Writes one agent result and nothing else. */
    public static <T> void write(
            PrintWriter out,
            String command,
            AgentResult<T> result,
            Object value,
            boolean includeSteps) {

        Map<String, Object> body =
                new LinkedHashMap<>();
        body.put("command", command);
        body.put("ok", result.succeeded());
        if (!result.succeeded()) {
            body.put("failure",
                result.failureReason()
                    .orElse("unknown"));
        }
        if (value != null) {
            body.put("value", value);
        }
        body.put("trace", trace(result.trace(),
                includeSteps));
        write(out, body);
    }

    /** Writes a result that involved no agent. */
    public static void write(
            PrintWriter out,
            String command,
            boolean ok,
            Object value) {

        Map<String, Object> body =
                new LinkedHashMap<>();
        body.put("command", command);
        body.put("ok", ok);
        body.put("value", value);
        write(out, body);
    }

    /** Writes a refusal that never reached a model. */
    public static void refusal(
            PrintWriter out,
            String command,
            String why) {

        Map<String, Object> body =
                new LinkedHashMap<>();
        body.put("command", command);
        body.put("ok", false);
        body.put("failure", why);
        write(out, body);
    }

    private static void write(
            PrintWriter out,
            Map<String, Object> body) {
        try {
            out.println(WRITER
                    .writeValueAsString(body));
        } catch (Exception broken) {
            out.println("{\"ok\": false,"
                + " \"failure\": \"the result"
                + " could not be written as"
                + " JSON\"}");
        }
        out.flush();
    }

    /**
     * What the agent did, in numbers.
     *
     * The steps are left out unless asked for.
     * They are the most useful part of a trace to
     * read and the least useful to assert on, and
     * a harness comparing runs does not want a
     * sentence of prose in the middle of its
     * figures.
     */
    public static Map<String, Object> trace(
            AgentTrace trace,
            boolean includeSteps) {

        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("modelCalls",
                trace.modelCalls());
        described.put("toolsCalled",
                trace.toolsCalled());
        described.put("validationFailures",
                trace.validationFailures());
        described.put("usage",
                usage(trace.usage()));
        if (includeSteps) {
            described.put("steps",
                    trace.steps());
        }
        return described;
    }

    public static Map<String, Object> usage(
            TokenUsage used) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("inputTokens",
                used.inputTokens());
        described.put("outputTokens",
                used.outputTokens());
        described.put("totalTokens",
                used.total());
        return described;
    }

    public static Map<String, Object> block(
            TrainingBlock block) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("id", block.id());
        described.put("startDate",
                block.startDate().toString());
        described.put("progression",
            block.progression().orElse(null));
        described.put("split",
            block.splitName().orElse(null));
        described.put("origin",
                block.origin().name());
        described.put("weeks",
                block.weeks().size());
        described.put("sessions",
            block.allSessions().size());

        List<Object> weeks = new ArrayList<>();
        for (Week week : block.weeks()) {
            Map<String, Object> described0 =
                    new LinkedHashMap<>();
            described0.put("number",
                    week.number());
            List<Object> sessions =
                    new ArrayList<>();
            for (Session session
                    : week.sessions()) {
                sessions.add(session(session));
            }
            described0.put("sessions",
                    sessions);
            weeks.add(described0);
        }
        described.put("week", weeks);
        return described;
    }

    public static Map<String, Object> session(
            Session session) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("id", session.id());
        described.put("date",
                session.date().toString());
        described.put("focus",
                session.focus().orElse(null));
        described.put("plannedTonnageKg",
                round(session
                        .plannedTonnage()));
        described.put("adjustmentNote",
            session.adjustmentNote()
                .orElse(null));

        List<Object> prescribed =
                new ArrayList<>();
        for (ExercisePrescription p
                : session.prescriptions()) {
            prescribed.add(prescription(p));
        }
        described.put("prescriptions",
                prescribed);
        return described;
    }

    public static Map<String, Object>
            prescription(
                ExercisePrescription p) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("id", p.id());
        described.put("exercise",
                p.exercise().name());
        described.put("exerciseId",
                p.exercise().id());
        described.put("lift",
            p.exercise().competitionLift()
                .map(Enum::name)
                .orElse(null));
        described.put("sets", p.sets());
        described.put("reps", p.reps());
        described.put("targetRpe",
            p.targetRpe().orElse(null));
        described.put("loadKg",
                round(p.loadKg()));
        return described;
    }

    public static Map<String, Object>
            substitution(
                Substitution substitution) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("replaced",
            substitution.replaced().name());
        described.put("replacement",
            substitution.replacement().name());
        described.put("reason",
            substitution.reason().name());
        described.put("loadKg",
            round(substitution.loadKg()));
        described.put("rationale",
            substitution.rationale()
                .orElse(null));
        described.put("prescription",
            prescription(
                    substitution.apply()));
        return described;
    }

    public static Map<String, Object> meal(
            MealSuggestion meal) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("name", meal.name());
        described.put("rationale",
            meal.rationale().orElse(null));
        described.put("totals",
                macros(meal.totals()));

        List<Object> portions =
                new ArrayList<>();
        for (MealSuggestion.Portion portion
                : meal.portions()) {
            Map<String, Object> described0 =
                    new LinkedHashMap<>();
            described0.put("food",
                    portion.food().name());
            described0.put("grams",
                    round(portion.grams()));
            described0.put("totals",
                macros(portion.totals()));
            portions.add(described0);
        }
        described.put("portions", portions);
        return described;
    }

    public static Map<String, Object> macros(
            MacroTotals totals) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("calories",
                round(totals.calories()));
        described.put("proteinG",
                round(totals.proteinG()));
        described.put("carbsG",
                round(totals.carbsG()));
        described.put("fatG",
                round(totals.fatG()));
        return described;
    }

    public static Map<String, Object> attempts(
            MeetAttempts attempts) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("lift",
                attempts.lift().name());
        described.put("openerKg",
                round(attempts.opener()));
        described.put("secondKg",
                round(attempts.second()));
        described.put("thirdKg",
                round(attempts.third()));
        described.put("totalJumpKg",
                round(attempts.totalJump()));
        described.put("rationale",
                attempts.rationale());
        return described;
    }

    public static Map<String, Object> testDay(
            TestDayPlan plan) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("lift",
                plan.lift().name());
        described.put("topSingleKg",
                round(plan.topSingle()));
        List<Object> warmups =
                new ArrayList<>();
        for (Double warmup : plan.warmups()) {
            warmups.add(round(warmup));
        }
        described.put("warmupsKg", warmups);
        described.put("totalSingles",
                plan.totalSingles());
        described.put("rationale",
                plan.rationale());
        return described;
    }

    public static Map<String, Object> adherence(
            PlanAdherence adherence) {
        Map<String, Object> described =
                new LinkedHashMap<>();
        described.put("empty",
                adherence.isEmpty());
        described.put("plannedSessions",
                adherence.planned());
        described.put("completedSessions",
                adherence.completed());
        described.put("plannedTonnageKg",
            round(adherence
                    .plannedTonnage()));
        described.put("actualTonnageKg",
            round(adherence.actualTonnage()));
        described.put("tonnageRate",
            round(adherence.tonnageRate()));
        described.put("sessionRate",
            round(adherence.sessionRate()));

        Map<String, Object> planned =
                new LinkedHashMap<>();
        adherence.plannedTopLoads().forEach(
            (lift, load) -> planned.put(
                lift.name(), round(load)));
        described.put("heaviestPlannedKg",
                planned);

        Map<String, Object> actual =
                new LinkedHashMap<>();
        adherence.actualTopLoads().forEach(
            (lift, load) -> actual.put(
                lift.name(), round(load)));
        described.put("heaviestDoneKg", actual);

        List<String> short0 =
                new ArrayList<>();
        adherence.liftsShortOfPlan().forEach(
            lift -> short0.add(lift.name()));
        described.put("shortOfPlan", short0);
        return described;
    }

    /**
     * Two decimal places.
     *
     * Loads are quoted to the nearest tenth of a
     * kilogram everywhere else, and a harness
     * comparing runs should not have to decide
     * whether 92.50000000000001 and 92.5 are the
     * same answer.
     */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
