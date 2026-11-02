package ca.yorku.eecs3311.spotter.agent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ca.yorku.eecs3311.spotter.agent.Tool;
import ca.yorku.eecs3311.spotter.agent
        .ToolSchema;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * Lets an agent see which movements are actually
 * available.
 *
 * Only exercises the lifter can perform are
 * returned: the equipment check and any recorded
 * limitation are applied here, before the model
 * sees anything. Filtering first is more reliable
 * than listing everything and instructing a model
 * to avoid some of it.
 */
public final class ExerciseDBTool
        implements Tool {

    /** Most names returned in one reply. */
    public static final int MAX_RESULTS = 40;

    private final ExerciseCatalog catalog;
    private final LifterProfile profile;

    public ExerciseDBTool(
            ExerciseCatalog catalog,
            LifterProfile profile) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        this.catalog = catalog;
        this.profile = profile;
    }

    @Override
    public String name() {
        return "available_exercises";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(),
            "Movements this lifter can actually"
            + " perform, given their equipment"
            + " and any injury on file. Only"
            + " prescribe exercises this"
            + " returns.",
            Map.of("pattern",
                "optional: filter by movement"
                + " pattern or part of a name,"
                + " such as squat, hinge or"
                + " press"));
    }

    @Override
    public String execute(
            Map<String, String> arguments) {
        String filter = arguments == null
                ? null
                : arguments.get("pattern");
        String wanted = filter == null
                ? ""
                : filter.trim()
                    .toLowerCase(Locale.ROOT);

        List<String> names = new ArrayList<>();
        for (Exercise exercise
                : catalog.all()) {
            if (profile != null
                    && !profile.canPerform(
                            exercise)) {
                continue;
            }
            if (!wanted.isEmpty()
                    && !matches(exercise,
                            wanted)) {
                continue;
            }
            names.add(exercise.name());
            if (names.size() >= MAX_RESULTS) {
                break;
            }
        }

        if (names.isEmpty()) {
            return wanted.isEmpty()
                ? "No exercises are available"
                  + " for this lifter."
                : "Nothing matching \"" + filter
                  + "\" is available. Ask again"
                  + " without a filter to see"
                  + " everything.";
        }
        return "Available (" + names.size()
            + "): " + String.join(", ", names);
    }

    private static boolean matches(
            Exercise exercise, String wanted) {
        if (exercise.name()
                .toLowerCase(Locale.ROOT)
                .contains(wanted)) {
            return true;
        }
        return exercise.movementPattern()
            .map(pattern -> pattern
                .toLowerCase(Locale.ROOT)
                .contains(wanted))
            .orElse(false);
    }
}
