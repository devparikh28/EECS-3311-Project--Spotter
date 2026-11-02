package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What onboarding has worked out so far.
 *
 * A profile cannot be half built, since
 * LifterProfile fills in defaults and would
 * happily accept a lifter who never said how
 * often they train. A draft can be half built,
 * and knows which answers are still missing.
 *
 * That distinction is the point. Guessing that
 * someone trains four days a week because most
 * people do would quietly shape every plan they
 * are given afterwards, and they would have no
 * way of knowing a number they never supplied is
 * driving their training. So the draft reports
 * what it does not know and the caller asks.
 */
public final class ProfileDraft {

    /** Lightest bodyweight treated as real. */
    public static final double MIN_BODYWEIGHT =
            30;

    /** Heaviest bodyweight treated as real. */
    public static final double MAX_BODYWEIGHT =
            300;

    private Double bodyweightKg;
    private Double weightClassKg;
    private TrainingGoal goal;
    private ExperienceLevel experience;
    private Integer trainingDaysPerWeek;
    private SplitPreference splitPreference;
    private WeightUnit displayUnit;
    private LocalDate meetDate;
    private final Set<Equipment> equipment =
            new LinkedHashSet<>();
    private final List<Limitation> limitations =
            new ArrayList<>();
    private DietaryConstraints diet;

    public ProfileDraft bodyweightKg(
            Double value) {
        this.bodyweightKg = value;
        return this;
    }

    public ProfileDraft weightClassKg(
            Double value) {
        this.weightClassKg = value;
        return this;
    }

    public ProfileDraft goal(
            TrainingGoal value) {
        this.goal = value;
        return this;
    }

    public ProfileDraft experience(
            ExperienceLevel value) {
        this.experience = value;
        return this;
    }

    public ProfileDraft trainingDaysPerWeek(
            Integer value) {
        this.trainingDaysPerWeek = value;
        return this;
    }

    public ProfileDraft splitPreference(
            SplitPreference value) {
        this.splitPreference = value;
        return this;
    }

    public ProfileDraft displayUnit(
            WeightUnit value) {
        this.displayUnit = value;
        return this;
    }

    public ProfileDraft meetDate(
            LocalDate value) {
        this.meetDate = value;
        return this;
    }

    public ProfileDraft addEquipment(
            Equipment value) {
        if (value != null) {
            equipment.add(value);
        }
        return this;
    }

    public ProfileDraft addLimitation(
            Limitation value) {
        if (value != null) {
            limitations.add(value);
        }
        return this;
    }

    public ProfileDraft diet(
            DietaryConstraints value) {
        this.diet = value;
        return this;
    }

    public Optional<Double> bodyweightKg() {
        return Optional.ofNullable(
                bodyweightKg);
    }

    public Optional<Double> weightClassKg() {
        return Optional.ofNullable(
                weightClassKg);
    }

    public Optional<TrainingGoal> goal() {
        return Optional.ofNullable(goal);
    }

    public Optional<ExperienceLevel>
            experience() {
        return Optional.ofNullable(experience);
    }

    public Optional<Integer>
            trainingDaysPerWeek() {
        return Optional.ofNullable(
                trainingDaysPerWeek);
    }

    public Optional<SplitPreference>
            splitPreference() {
        return Optional.ofNullable(
                splitPreference);
    }

    public Optional<WeightUnit> displayUnit() {
        return Optional.ofNullable(displayUnit);
    }

    public Optional<LocalDate> meetDate() {
        return Optional.ofNullable(meetDate);
    }

    public Set<Equipment> equipment() {
        return Set.copyOf(equipment);
    }

    public List<Limitation> limitations() {
        return List.copyOf(limitations);
    }

    public Optional<DietaryConstraints> diet() {
        return Optional.ofNullable(diet);
    }

    /**
     * The answers still needed, in plain words a
     * lifter could be asked directly.
     */
    public List<String> missing() {
        List<String> questions =
                new ArrayList<>();
        if (bodyweightKg == null) {
            questions.add("how much they weigh");
        }
        if (goal == null) {
            questions.add("what they are"
                + " training for");
        }
        if (experience == null) {
            questions.add("how long they have"
                + " been lifting");
        }
        if (trainingDaysPerWeek == null) {
            questions.add("how many days a week"
                + " they train");
        }
        if (equipment.isEmpty()) {
            questions.add("what equipment they"
                + " have");
        }
        return questions;
    }

    public boolean isComplete() {
        return missing().isEmpty();
    }

    /**
     * Anything stated that cannot be true, as
     * opposed to anything merely absent.
     */
    public List<String> implausible() {
        List<String> problems =
                new ArrayList<>();
        if (bodyweightKg != null
                && (bodyweightKg
                        < MIN_BODYWEIGHT
                    || bodyweightKg
                        > MAX_BODYWEIGHT)) {
            problems.add(String.format(
                "a bodyweight of %.1f kg is not"
                + " a real figure",
                bodyweightKg));
        }
        if (trainingDaysPerWeek != null
                && (trainingDaysPerWeek < 1
                    || trainingDaysPerWeek > 7)) {
            problems.add("training "
                + trainingDaysPerWeek
                + " days a week is not"
                + " possible");
        }
        if (weightClassKg != null
                && weightClassKg <= 0) {
            problems.add("a weight class must be"
                + " above zero");
        }
        return problems;
    }

    /**
     * The finished profile.
     *
     * @throws IllegalStateException when answers
     *         are still missing, because a
     *         profile with invented values is
     *         worse than none
     */
    public LifterProfile toProfile() {
        if (!isComplete()) {
            throw new IllegalStateException(
                "The profile is not finished;"
                + " still needed: "
                + String.join(", ", missing()));
        }
        LifterProfile.Builder builder =
            LifterProfile.with(goal, experience)
                .bodyweightKg(bodyweightKg)
                .trainingDaysPerWeek(
                        trainingDaysPerWeek)
                .equipment(equipment);

        if (weightClassKg != null) {
            builder.weightClassKg(weightClassKg);
        }
        if (splitPreference != null) {
            builder.splitPreference(
                    splitPreference);
        }
        if (displayUnit != null) {
            builder.displayUnit(displayUnit);
        }
        if (meetDate != null) {
            builder.meetDate(meetDate);
        }
        if (diet != null) {
            builder.diet(diet);
        }
        for (Limitation limitation
                : limitations) {
            builder.addLimitation(limitation);
        }
        return builder.build();
    }
}
