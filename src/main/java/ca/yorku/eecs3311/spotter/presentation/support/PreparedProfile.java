package ca.yorku.eecs3311.spotter
        .presentation.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Onboarding without a model, for when there is
 * no API key.
 *
 * This reads a lifter's paragraph with keyword
 * matching, which is exactly the approach a model
 * replaces. It is kept deliberately crude and is
 * worth looking at for that reason: it handles
 * the sentences it was written for and falls over
 * on anything phrased differently, which is the
 * clearest available demonstration of what the
 * model is actually being paid for.
 *
 * Whatever it cannot find it leaves out, the same
 * rule the agent follows, so the screen asks the
 * lifter rather than inventing an answer.
 */
public final class PreparedProfile {

    /** Words for small numbers, as people write. */
    private static final Map<String, Integer>
            NUMBERS = Map.of(
                "one", 1, "two", 2, "three", 3,
                "four", 4, "five", 5, "six", 6,
                "seven", 7);

    private static final Pattern KILOS =
        Pattern.compile(
            "(\\d{2,3}(?:\\.\\d+)?)\\s?(kg|kilos|"
            + "kilograms)");

    private static final Pattern POUNDS =
        Pattern.compile(
            "(\\d{2,3}(?:\\.\\d+)?)\\s?(lb|lbs|"
            + "pounds)");

    private static final Pattern DAYS =
        Pattern.compile(
            "(\\d|one|two|three|four|five|six|"
            + "seven)\\s?(?:days?|times?|x)\\s?"
            + "(?:a|per)?\\s?week");

    private static final Pattern YEARS =
        Pattern.compile(
            "(\\d{1,2}|one|two|three|four|five|"
            + "six|seven)\\s?(?:\\+)?\\s?years?");

    private PreparedProfile() {
    }

    /**
     * The JSON an agent would have produced.
     *
     * @param said the lifter's own words
     */
    public static String of(String said) {
        String text = said == null
                ? ""
                : said.toLowerCase(Locale.ROOT);

        List<String> fields = new ArrayList<>();

        bodyweight(text).ifPresent(kg ->
            fields.add(String.format(
                "\"bodyweightKg\":%.1f", kg)));
        if (POUNDS.matcher(text).find()
                && !KILOS.matcher(text).find()) {
            fields.add(
                "\"displayUnit\":\"LB\"");
        }
        goal(text).ifPresent(goal ->
            fields.add("\"goal\":\"" + goal
                    + "\""));
        experience(text).ifPresent(level ->
            fields.add("\"experience\":\""
                    + level + "\""));
        days(text).ifPresent(days ->
            fields.add(
                "\"trainingDaysPerWeek\":"
                + days));

        List<String> equipment =
                equipment(text);
        if (!equipment.isEmpty()) {
            fields.add("\"equipment\":[\""
                + String.join("\",\"", equipment)
                + "\"]");
        }
        diet(text).ifPresent(fields::add);
        limitation(text).ifPresent(fields::add);

        return "{" + String.join(",", fields)
                + "}";
    }

    private static java.util.Optional<Double>
            bodyweight(String text) {
        Matcher kilos = KILOS.matcher(text);
        if (kilos.find()) {
            return java.util.Optional.of(
                Double.parseDouble(
                        kilos.group(1)));
        }
        Matcher pounds = POUNDS.matcher(text);
        if (pounds.find()) {
            double lb = Double.parseDouble(
                    pounds.group(1));
            return java.util.Optional.of(
                Math.round(lb * 0.4536 * 10)
                    / 10.0);
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<Integer>
            days(String text) {
        Matcher found = DAYS.matcher(text);
        if (!found.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                numberOf(found.group(1)));
    }

    private static int numberOf(String token) {
        Integer word = NUMBERS.get(token);
        if (word != null) {
            return word;
        }
        return Integer.parseInt(token);
    }

    private static java.util.Optional<String>
            goal(String text) {
        if (contains(text, "meet", "compete",
                "competition", "platform")) {
            return java.util.Optional.of(
                    "MEET_PREP");
        }
        if (contains(text, "stronger",
                "strength", "strong")) {
            return java.util.Optional.of(
                    "STRENGTH");
        }
        if (contains(text, "size", "muscle",
                "hypertrophy", "bigger")) {
            return java.util.Optional.of(
                    "HYPERTROPHY");
        }
        if (contains(text, "fit", "fitness",
                "health")) {
            return java.util.Optional.of(
                    "GENERAL_FITNESS");
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<String>
            experience(String text) {
        if (contains(text, "new to", "novice",
                "beginner", "just started",
                "first time")) {
            return java.util.Optional.of(
                    "NOVICE");
        }
        Matcher years = YEARS.matcher(text);
        if (years.find()) {
            int counted =
                    numberOf(years.group(1));
            if (counted <= 1) {
                return java.util.Optional.of(
                        "NOVICE");
            }
            return java.util.Optional.of(
                counted >= 5
                    ? "ADVANCED"
                    : "INTERMEDIATE");
        }
        if (contains(text, "advanced",
                "competitive")) {
            return java.util.Optional.of(
                    "ADVANCED");
        }
        return java.util.Optional.empty();
    }

    /**
     * A commercial gym is taken to mean the usual
     * barbell equipment, which is the one
     * inference allowed here because a lifter
     * saying "commercial gym" has told you what
     * is there.
     */
    private static List<String> equipment(
            String text) {
        List<String> found = new ArrayList<>();
        if (contains(text, "commercial gym",
                "fully equipped", "proper gym",
                "full gym")) {
            found.add("BARBELL");
            found.add("SQUAT_RACK");
            found.add("BENCH");
            found.add("DUMBBELL");
            found.add("CABLE");
            found.add("LAT_PULLDOWN");
            return found;
        }
        addIf(text, found, "barbell",
                "BARBELL");
        addIf(text, found, "rack",
                "SQUAT_RACK");
        addIf(text, found, "bench", "BENCH");
        addIf(text, found, "dumbbell",
                "DUMBBELL");
        addIf(text, found, "kettlebell",
                "KETTLEBELL");
        addIf(text, found, "pull up bar",
                "PULL_UP_BAR");
        addIf(text, found, "band", "BANDS");
        addIf(text, found, "leg press",
                "LEG_PRESS");
        return found;
    }

    private static void addIf(
            String text,
            List<String> found,
            String word,
            String equipment) {
        if (text.contains(word)) {
            found.add(equipment);
        }
    }

    private static java.util.Optional<String>
            diet(String text) {
        boolean vegetarian = contains(text,
                "vegetarian", "vegan");
        boolean noDairy = contains(text,
                "no dairy", "dairy free",
                "lactose", "vegan");
        boolean noEggs = contains(text,
                "no egg", "egg free", "vegan");

        if (!vegetarian && !noDairy && !noEggs) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
            String.format(
                "\"diet\":{\"vegetarian\":%b,"
                + "\"allowsDairy\":%b,"
                + "\"allowsEggs\":%b}",
                vegetarian, !noDairy, !noEggs));
    }

    /**
     * One limitation, when a body part is
     * mentioned alongside a complaint. Crude on
     * purpose: it finds the sentence it was
     * written for and nothing subtler.
     */
    private static java.util.Optional<String>
            limitation(String text) {
        String[][] parts = {
            {"shoulder", "horizontal press"},
            {"knee", "squat"},
            {"back", "hinge"},
            {"elbow", "horizontal press"},
            {"hip", "squat"}};

        boolean complains = contains(text,
            "hurt", "objects", "sore", "injur",
            "pain", "bad ", "tweak", "niggl");
        if (!complains) {
            return java.util.Optional.empty();
        }

        for (String[] part : parts) {
            if (text.contains(part[0])) {
                return java.util.Optional.of(
                    String.format(
                        "\"limitations\":"
                        + "[{\"description\":"
                        + "\"%s is a problem\","
                        + "\"avoidPatterns\":"
                        + "[\"%s\"],"
                        + "\"painful\":true}]",
                        part[0], part[1]));
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean contains(
            String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
