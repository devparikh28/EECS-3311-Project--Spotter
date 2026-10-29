package ca.yorku.eecs3311.spotter.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Turns a model's reply into domain objects.
 *
 * The important rule here is what is read and
 * what is ignored. Structure comes from the
 * reply: how many weeks, which sessions, how many
 * sets. Facts do not. An exercise is resolved
 * through the catalog by name, and the catalog's
 * entry is used, so whatever the model believes
 * about a movement's equipment or muscle groups
 * is discarded in favour of what Spotter knows.
 *
 * A reply naming an exercise that does not exist
 * fails here rather than becoming a prescription
 * nobody can perform.
 */
public final class ResponseParser {

    private final ObjectMapper json =
            new ObjectMapper();
    private final ExerciseCatalog catalog;
    private final List<String> problems =
            new ArrayList<>();

    public ResponseParser(
            ExerciseCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        this.catalog = catalog;
    }

    /** Why the last parse failed, if it did. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /**
     * Finds the JSON in a reply.
     *
     * Models often wrap JSON in prose or a code
     * fence however firmly they are told not to,
     * so the object is located rather than the
     * whole reply being parsed.
     */
    public Optional<JsonNode> extractJson(
            String reply) {
        if (reply == null || reply.isBlank()) {
            return Optional.empty();
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readTree(
                reply.substring(start,
                        end + 1)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Reads a training block from a reply.
     *
     * @param reply     what the model said
     * @param startDate the block's first day
     */
    public Optional<TrainingBlock> toBlock(
            String reply, LocalDate startDate) {
        problems.clear();
        Optional<JsonNode> root =
                extractJson(reply);
        if (root.isEmpty()) {
            problems.add("no JSON object was"
                    + " found in the reply");
            return Optional.empty();
        }
        JsonNode node = root.get();

        JsonNode weeksNode = node.get("weeks");
        if (weeksNode == null
                || !weeksNode.isArray()
                || weeksNode.isEmpty()) {
            problems.add("the reply has no"
                    + " \"weeks\" array");
            return Optional.empty();
        }

        List<Week> weeks = new ArrayList<>();
        int prescriptionId = 1;
        int sessionId = 1;

        for (JsonNode weekNode : weeksNode) {
            int number = weekNode
                    .path("number")
                    .asInt(weeks.size() + 1);
            JsonNode sessionsNode =
                    weekNode.get("sessions");
            if (sessionsNode == null
                    || !sessionsNode.isArray()) {
                problems.add("week " + number
                    + " has no \"sessions\""
                    + " array");
                return Optional.empty();
            }

            List<Session> sessions =
                    new ArrayList<>();
            for (JsonNode sessionNode
                    : sessionsNode) {
                Optional<Session> session =
                    toSession(sessionNode,
                        startDate, number,
                        sessionId++,
                        prescriptionId);
                if (session.isEmpty()) {
                    return Optional.empty();
                }
                prescriptionId += session.get()
                    .prescriptions().size();
                sessions.add(session.get());
            }
            weeks.add(new Week(number,
                    sessions));
        }

        try {
            return Optional.of(
                new TrainingBlock(0, startDate,
                    node.path("progression")
                        .asText(null),
                    node.path("splitName")
                        .asText(null),
                    BlockOrigin.GENERATED,
                    weeks));
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Session> toSession(
            JsonNode node,
            LocalDate blockStart,
            int weekNumber,
            int sessionId,
            int firstPrescriptionId) {

        LocalDate date = dateOf(node,
                blockStart, weekNumber);

        JsonNode exercisesNode =
                node.get("exercises");
        if (exercisesNode == null
                || !exercisesNode.isArray()
                || exercisesNode.isEmpty()) {
            problems.add("a session on " + date
                + " has no exercises");
            return Optional.empty();
        }

        List<ExercisePrescription>
            prescriptions = new ArrayList<>();
        int id = firstPrescriptionId;

        for (JsonNode exerciseNode
                : exercisesNode) {
            String name = exerciseNode
                    .path("name").asText("");
            Optional<Exercise> exercise =
                    catalog.byName(name);
            if (exercise.isEmpty()) {
                problems.add("there is no"
                    + " exercise called \""
                    + name + "\" in the"
                    + " catalog");
                return Optional.empty();
            }
            try {
                prescriptions.add(
                    new ExercisePrescription(
                        id++,
                        exercise.get(),
                        exerciseNode.path("sets")
                            .asInt(0),
                        exerciseNode.path("reps")
                            .asInt(0),
                        rpeOf(exerciseNode),
                        exerciseNode
                            .path("loadKg")
                            .asDouble(0)));
            } catch (IllegalArgumentException e) {
                problems.add("\"" + name
                    + "\": " + e.getMessage());
                return Optional.empty();
            }
        }

        return Optional.of(new Session(sessionId,
            date,
            node.path("focus").asText(null),
            prescriptions));
    }

    private static Double rpeOf(JsonNode node) {
        JsonNode rpe = node.get("rpe");
        return rpe == null || rpe.isNull()
                ? null
                : rpe.asDouble();
    }

    /**
     * A session's date. A readable date in the
     * reply is used; otherwise one is worked out
     * from the week number, because a missing
     * date is not a reason to reject an
     * otherwise sound plan.
     */
    private static LocalDate dateOf(
            JsonNode node,
            LocalDate blockStart,
            int weekNumber) {
        String text =
                node.path("date").asText("");
        if (!text.isBlank()) {
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException ignored) {
                // fall through to the estimate
            }
        }
        return blockStart.plusWeeks(
                weekNumber - 1L);
    }
}
