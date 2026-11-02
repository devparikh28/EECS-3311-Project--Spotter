package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Who Spotter is advising.
 *
 * Everything else reads from here before deciding
 * anything. Experience chooses the progression,
 * equipment limits what can be prescribed,
 * limitations remove movements before they are
 * considered, and the meet date is optional
 * throughout because most lifters do not have
 * one.
 */
public final class LifterProfile {

    private final int id;
    private final double bodyweightKg;
    private final Double weightClassKg;
    private final TrainingGoal goal;
    private final ExperienceLevel experience;
    private final SplitPreference splitPreference;
    private final WeightUnit displayUnit;
    private final LocalDate meetDate;
    private final int trainingDaysPerWeek;
    private final Set<Equipment> equipment;
    private final List<Limitation> limitations;
    private final DietaryConstraints diet;

    private LifterProfile(Builder builder) {
        this.id = builder.id;
        this.bodyweightKg = builder.bodyweightKg;
        this.weightClassKg = builder.weightClassKg;
        this.goal = builder.goal;
        this.experience = builder.experience;
        this.splitPreference =
                builder.splitPreference;
        this.displayUnit = builder.displayUnit;
        this.meetDate = builder.meetDate;
        this.trainingDaysPerWeek =
                builder.trainingDaysPerWeek;
        this.equipment =
            Collections.unmodifiableSet(
                new LinkedHashSet<>(
                    builder.equipment));
        this.limitations =
            Collections.unmodifiableList(
                new ArrayList<>(
                    builder.limitations));
        this.diet = builder.diet;
    }

    public static Builder with(
            TrainingGoal goal,
            ExperienceLevel experience) {
        return new Builder(goal, experience);
    }

    public int id() {
        return id;
    }

    public double bodyweightKg() {
        return bodyweightKg;
    }

    public Optional<Double> weightClassKg() {
        return Optional.ofNullable(
                weightClassKg);
    }

    public TrainingGoal goal() {
        return goal;
    }

    public ExperienceLevel experience() {
        return experience;
    }

    public SplitPreference splitPreference() {
        return splitPreference;
    }

    public WeightUnit displayUnit() {
        return displayUnit;
    }

    public int trainingDaysPerWeek() {
        return trainingDaysPerWeek;
    }

    public Set<Equipment> equipment() {
        return equipment;
    }

    public List<Limitation> limitations() {
        return limitations;
    }

    public DietaryConstraints diet() {
        return diet;
    }

    public Optional<LocalDate> meetDate() {
        return Optional.ofNullable(meetDate);
    }

    public boolean hasMeetDate() {
        return meetDate != null;
    }

    /** Whole weeks until the meet, if there is one. */
    public Optional<Integer> weeksUntilMeet(
            LocalDate today) {
        if (meetDate == null || today == null
                || meetDate.isBefore(today)) {
            return Optional.empty();
        }
        return Optional.of((int)
            ChronoUnit.WEEKS.between(today,
                    meetDate));
    }

    public boolean hasEquipment(
            Equipment piece) {
        return equipment.contains(piece);
    }

    /**
     * True when nothing on file rules this
     * exercise out and the gym has what it
     * needs. This is the filter that runs before
     * the agent is offered anything.
     */
    public boolean canPerform(Exercise exercise) {
        if (exercise == null) {
            return false;
        }
        if (!exercise.isPerformableWith(
                equipment)) {
            return false;
        }
        for (Limitation limitation
                : limitations) {
            if (limitation.rulesOut(exercise)) {
                return false;
            }
        }
        return true;
    }

    /** Builds a profile, filling in sane defaults. */
    public static final class Builder {

        private int id;
        private double bodyweightKg = 80;
        private Double weightClassKg;
        private final TrainingGoal goal;
        private final ExperienceLevel experience;
        private SplitPreference splitPreference =
                SplitPreference.AUTO;
        private WeightUnit displayUnit =
                WeightUnit.KG;
        private LocalDate meetDate;
        private int trainingDaysPerWeek = 4;
        private Set<Equipment> equipment =
                new LinkedHashSet<>();
        private List<Limitation> limitations =
                new ArrayList<>();
        private DietaryConstraints diet =
                DietaryConstraints.none();

        private Builder(TrainingGoal goal,
                ExperienceLevel experience) {
            if (goal == null
                    || experience == null) {
                throw new IllegalArgumentException(
                    "A profile needs a goal and"
                    + " an experience level");
            }
            this.goal = goal;
            this.experience = experience;
        }

        public Builder id(int value) {
            this.id = value;
            return this;
        }

        public Builder bodyweightKg(
                double value) {
            if (value <= 0) {
                throw new IllegalArgumentException(
                    "Bodyweight must be above"
                    + " zero");
            }
            this.bodyweightKg = value;
            return this;
        }

        public Builder weightClassKg(
                double value) {
            this.weightClassKg = value;
            return this;
        }

        public Builder splitPreference(
                SplitPreference value) {
            this.splitPreference = value == null
                    ? SplitPreference.AUTO
                    : value;
            return this;
        }

        public Builder displayUnit(
                WeightUnit value) {
            this.displayUnit = value == null
                    ? WeightUnit.KG
                    : value;
            return this;
        }

        /** Optional: most lifters have no meet. */
        public Builder meetDate(LocalDate value) {
            this.meetDate = value;
            return this;
        }

        public Builder trainingDaysPerWeek(
                int value) {
            if (value < 1 || value > 7) {
                throw new IllegalArgumentException(
                    "Training days must be"
                    + " between 1 and 7, but"
                    + " was " + value);
            }
            this.trainingDaysPerWeek = value;
            return this;
        }

        public Builder equipment(
                Set<Equipment> value) {
            this.equipment = value == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(value);
            return this;
        }

        public Builder addLimitation(
                Limitation value) {
            if (value != null) {
                this.limitations.add(value);
            }
            return this;
        }

        public Builder diet(
                DietaryConstraints value) {
            this.diet = value == null
                    ? DietaryConstraints.none()
                    : value;
            return this;
        }

        public LifterProfile build() {
            return new LifterProfile(this);
        }
    }
}
