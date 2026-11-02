package ca.yorku.eecs3311.spotter.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ChatReply;
import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MealSuggestion;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .MeetAttempts;
import ca.yorku.eecs3311.spotter.domain
        .ProfileDraft;
import ca.yorku.eecs3311.spotter.domain
        .SplitPreference;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;
import ca.yorku.eecs3311.spotter.domain
        .SubstitutionReason;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;

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
    private final FoodCatalog foods;
    private final List<String> problems =
            new ArrayList<>();

    public ResponseParser(
            ExerciseCatalog catalog) {
        this(catalog, null);
    }

    /**
     * A parser that can also read meals.
     *
     * The food catalog is separate because most
     * agents never touch food, and a parser for
     * those should not require one.
     */
    public ResponseParser(
            ExerciseCatalog catalog,
            FoodCatalog foods) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        this.catalog = catalog;
        this.foods = foods;
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

    /**
     * Reads a chat answer from a reply.
     *
     * The reply itself is prose and is taken as
     * written, since there is nothing to resolve
     * against the catalogues. The note beside it
     * is kept separate rather than merged in,
     * because the two are judged differently:
     * the answer is shown to the lifter and the
     * note has to pass the memory gate first.
     *
     * @param reply what the model said
     */
    public Optional<ChatReply> toChatReply(
            String reply) {
        problems.clear();
        Optional<JsonNode> root =
                extractJson(reply);
        if (root.isEmpty()) {
            problems.add("no JSON object was"
                    + " found in the reply");
            return Optional.empty();
        }
        JsonNode node = root.get();

        String said = node.path("reply")
                .asText("");
        if (said.isBlank()) {
            problems.add("the reply has no"
                    + " \"reply\" text");
            return Optional.empty();
        }

        String remember = node
                .hasNonNull("remember")
            ? node.get("remember").asText(null)
            : null;

        try {
            return Optional.of(new ChatReply(
                    said, remember));
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads an onboarding draft from a reply.
     *
     * Every value is taken as a name to look up
     * rather than as text to keep. A goal, an
     * experience level, a split and a unit must
     * each resolve to something the domain
     * already defines, and so must every piece
     * of equipment, so a model cannot introduce
     * a goal Spotter has no progression for or a
     * rack that does not exist.
     *
     * A field the reply leaves out stays absent
     * rather than being filled in. That is the
     * whole reason a draft exists: an invented
     * training frequency would shape every plan
     * the lifter is ever given, and they would
     * have no way of knowing.
     *
     * @param reply what the model said
     */
    public Optional<ProfileDraft> toProfileDraft(
            String reply) {
        problems.clear();
        Optional<JsonNode> root =
                extractJson(reply);
        if (root.isEmpty()) {
            problems.add("no JSON object was"
                    + " found in the reply");
            return Optional.empty();
        }
        JsonNode node = root.get();
        ProfileDraft draft = new ProfileDraft();

        if (node.hasNonNull("bodyweightKg")) {
            draft.bodyweightKg(node
                .get("bodyweightKg")
                .asDouble());
        }
        if (node.hasNonNull("weightClassKg")) {
            draft.weightClassKg(node
                .get("weightClassKg")
                .asDouble());
        }
        if (node.hasNonNull(
                "trainingDaysPerWeek")) {
            draft.trainingDaysPerWeek(node
                .get("trainingDaysPerWeek")
                .asInt());
        }

        readEnum(node, "goal",
                TrainingGoal.class)
            .ifPresent(draft::goal);
        readEnum(node, "experience",
                ExperienceLevel.class)
            .ifPresent(draft::experience);
        readEnum(node, "splitPreference",
                SplitPreference.class)
            .ifPresent(draft::splitPreference);
        readEnum(node, "displayUnit",
                WeightUnit.class)
            .ifPresent(draft::displayUnit);

        if (node.hasNonNull("meetDate")) {
            String text = node.get("meetDate")
                    .asText("");
            try {
                draft.meetDate(
                        LocalDate.parse(text));
            } catch (RuntimeException e) {
                problems.add("\"" + text
                    + "\" is not a date in the"
                    + " form YYYY-MM-DD");
                return Optional.empty();
            }
        }

        JsonNode equipment =
                node.get("equipment");
        if (equipment != null
                && equipment.isArray()) {
            for (JsonNode item : equipment) {
                String name =
                        item.asText("");
                Optional<Equipment> resolved =
                    constantOf(Equipment.class,
                            name);
                if (resolved.isEmpty()) {
                    problems.add("\"" + name
                        + "\" is not equipment"
                        + " Spotter knows"
                        + " about");
                    return Optional.empty();
                }
                draft.addEquipment(
                        resolved.get());
            }
        }

        JsonNode diet = node.get("diet");
        if (diet != null && diet.isObject()) {
            draft.diet(new DietaryConstraints(
                diet.path("vegetarian")
                    .asBoolean(false),
                diet.path("allowsDairy")
                    .asBoolean(true),
                diet.path("allowsEggs")
                    .asBoolean(true)));
        }

        JsonNode limitations =
                node.get("limitations");
        if (limitations != null
                && limitations.isArray()) {
            for (JsonNode item : limitations) {
                Optional<Limitation> limitation =
                        toLimitation(item);
                if (limitation.isEmpty()) {
                    return Optional.empty();
                }
                draft.addLimitation(
                        limitation.get());
            }
        }
        return Optional.of(draft);
    }

    private Optional<Limitation> toLimitation(
            JsonNode node) {
        String description = node
                .path("description").asText("");
        if (description.isBlank()) {
            problems.add("a limitation was given"
                + " with no description");
            return Optional.empty();
        }
        try {
            return Optional.of(new Limitation(
                description,
                textSet(node, "avoidPatterns"),
                textSet(node, "avoidExercises"),
                node.path("painful")
                    .asBoolean(false)));
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return Optional.empty();
        }
    }

    private static Set<String> textSet(
            JsonNode node, String field) {
        Set<String> values =
                new LinkedHashSet<>();
        JsonNode array = node.get(field);
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String text = item.asText("");
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    /**
     * One of an enum's constants by name,
     * ignoring case and treating spaces and
     * hyphens as underscores, since a model
     * writes "upper lower" as readily as
     * UPPER_LOWER.
     */
    private <E extends Enum<E>> Optional<E>
            readEnum(JsonNode node,
                     String field,
                     Class<E> type) {
        if (!node.hasNonNull(field)) {
            return Optional.empty();
        }
        String name = node.get(field)
                .asText("");
        Optional<E> resolved =
                constantOf(type, name);
        if (resolved.isEmpty()
                && !name.isBlank()) {
            problems.add("\"" + name + "\" is"
                + " not a " + field
                + " Spotter knows about");
        }
        return resolved;
    }

    private static <E extends Enum<E>>
            Optional<E> constantOf(
                Class<E> type, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        for (E constant
                : type.getEnumConstants()) {
            if (constant.name()
                    .equals(wanted)) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    /**
     * Reads a meal suggestion from a reply.
     *
     * Foods are resolved through the food
     * catalog, so a model inventing one fails
     * here. Nothing nutritional is read from the
     * reply at all: only the food names and the
     * portions in grams. Whatever the model
     * believes a chicken breast contains is
     * discarded in favour of what the catalog
     * says, and the meal's totals are then
     * arithmetic.
     *
     * @param reply what the model said
     */
    public Optional<MealSuggestion> toMeal(
            String reply) {
        problems.clear();
        if (foods == null) {
            problems.add("this parser was not"
                + " given a food catalog");
            return Optional.empty();
        }
        Optional<JsonNode> root =
                extractJson(reply);
        if (root.isEmpty()) {
            problems.add("no JSON object was"
                    + " found in the reply");
            return Optional.empty();
        }
        JsonNode node = root.get();

        JsonNode foodsNode = node.get("foods");
        if (foodsNode == null
                || !foodsNode.isArray()
                || foodsNode.isEmpty()) {
            problems.add("the reply has no"
                    + " \"foods\" array");
            return Optional.empty();
        }

        List<MealSuggestion.Portion> portions =
                new ArrayList<>();
        for (JsonNode foodNode : foodsNode) {
            String name = foodNode.path("name")
                    .asText("");
            Optional<FoodItem> food =
                    this.foods.byName(name);
            if (food.isEmpty()) {
                problems.add("there is no food"
                    + " called \"" + name
                    + "\" in the catalog");
                return Optional.empty();
            }
            double grams = foodNode
                    .path("grams").asDouble(0);
            try {
                portions.add(
                    new MealSuggestion.Portion(
                        food.get(), grams));
            } catch (IllegalArgumentException e) {
                problems.add("\"" + name
                    + "\": " + e.getMessage());
                return Optional.empty();
            }
        }

        try {
            return Optional.of(
                new MealSuggestion(
                    node.path("name")
                        .asText(null),
                    portions,
                    node.path("rationale")
                        .asText(null)));
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads an explanation of meet attempts.
     *
     * The three weights are not read from the
     * reply at all. They were calculated before
     * the model was asked anything and are
     * carried through untouched; only the words
     * come from the reply. A model cannot change
     * a competition attempt by talking about it.
     *
     * @param reply    what the model said
     * @param attempts the calculated attempts
     */
    public Optional<MeetAttempts> toRationale(
            String reply,
            MeetAttempts attempts) {
        problems.clear();
        if (attempts == null) {
            problems.add("there are no attempts"
                    + " to explain");
            return Optional.empty();
        }
        Optional<JsonNode> root =
                extractJson(reply);
        if (root.isEmpty()) {
            problems.add("no JSON object was"
                    + " found in the reply");
            return Optional.empty();
        }

        String rationale = root.get()
                .path("rationale").asText("");
        if (rationale.isBlank()) {
            problems.add("the reply has no"
                + " \"rationale\" text");
            return Optional.empty();
        }

        return Optional.of(new MeetAttempts(
                attempts.lift(),
                attempts.opener(),
                attempts.second(),
                attempts.third(),
                rationale.strip()));
    }

    /**
     * Reads an adjusted session from a reply.
     *
     * Only the sets and the load of prescriptions
     * already in the session can be changed.
     * Exercises are matched by the id they came
     * with, so a reply cannot add a movement,
     * rename one, or address a prescription that
     * is not in today's session. That is what
     * makes this safe to apply without consulting
     * the catalog at all.
     *
     * A prescription the reply does not mention
     * keeps what it had.
     *
     * @param reply    what the model said
     * @param original today's session
     */
    public Optional<Session> toAdjustedSession(
            String reply, Session original) {
        problems.clear();
        if (original == null) {
            problems.add("there is no session"
                    + " to adjust");
            return Optional.empty();
        }
        Optional<JsonNode> root =
                extractJson(reply);
        if (root.isEmpty()) {
            problems.add("no JSON object was"
                    + " found in the reply");
            return Optional.empty();
        }
        JsonNode node = root.get();

        JsonNode exercisesNode =
                node.get("exercises");
        if (exercisesNode == null
                || !exercisesNode.isArray()) {
            problems.add("the reply has no"
                    + " \"exercises\" array");
            return Optional.empty();
        }

        List<ExercisePrescription> adjusted =
                new ArrayList<>(
                    original.prescriptions());

        for (JsonNode exerciseNode
                : exercisesNode) {
            JsonNode idNode =
                    exerciseNode.get("id");
            if (idNode == null
                    || !idNode.canConvertToInt()) {
                problems.add("an entry has no"
                    + " numeric \"id\", so"
                    + " there is no way to tell"
                    + " which exercise it"
                    + " means");
                return Optional.empty();
            }
            int id = idNode.asInt();

            int index = indexOf(adjusted, id);
            if (index < 0) {
                problems.add("today's session"
                    + " has no exercise with id "
                    + id);
                return Optional.empty();
            }

            ExercisePrescription before =
                    adjusted.get(index);
            double loadKg = exerciseNode
                    .path("loadKg")
                    .asDouble(before.loadKg());
            int sets = exerciseNode.path("sets")
                    .asInt(before.sets());

            try {
                adjusted.set(index,
                    before.withLoad(loadKg)
                        .withSets(sets));
            } catch (IllegalArgumentException e) {
                problems.add(before.exercise()
                    .name() + ": "
                    + e.getMessage());
                return Optional.empty();
            }
        }

        Session session = new Session(
                original.id(), original.date(),
                original.focus().orElse(null),
                adjusted);
        String note = node.path("note")
                .asText(null);
        if (note != null && !note.isBlank()) {
            session.setAdjustmentNote(
                    note.strip());
        }
        return Optional.of(session);
    }

    private static int indexOf(
            List<ExercisePrescription> list,
            int id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Reads a substitution from a reply.
     *
     * The replacement is resolved through the
     * catalog like any other exercise, so a model
     * that invents a movement fails here. Sets
     * and reps are taken from the prescription
     * being replaced rather than from the reply,
     * because only the movement and the load were
     * being asked about.
     *
     * @param reply    what the model said
     * @param original the prescription to replace
     * @param reason   why it is being replaced
     */
    public Optional<Substitution> toSubstitution(
            String reply,
            ExercisePrescription original,
            SubstitutionReason reason) {
        problems.clear();
        if (original == null) {
            problems.add("there is no"
                + " prescription to replace");
            return Optional.empty();
        }
        Optional<JsonNode> root =
                extractJson(reply);
        if (root.isEmpty()) {
            problems.add("no JSON object was"
                    + " found in the reply");
            return Optional.empty();
        }
        JsonNode node = root.get();

        String name =
                node.path("name").asText("");
        Optional<Exercise> replacement =
                catalog.byName(name);
        if (replacement.isEmpty()) {
            problems.add("there is no exercise"
                + " called \"" + name + "\" in"
                + " the catalog");
            return Optional.empty();
        }

        double loadKg = node.path("loadKg")
                .asDouble(original.loadKg());
        String rationale = node
                .path("rationale")
                .asText(null);

        try {
            return Optional.of(
                new Substitution(original,
                    replacement.get(), loadKg,
                    reason, rationale));
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
