package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * How recovered the lifter was on one day.
 *
 * Every reading is optional because the two
 * sources carry different things: a wearable
 * export has a recovery score, HRV and sleep,
 * while a manual check in has sleep, soreness
 * and energy. The rule engine works with
 * whatever is present.
 */
public final class RecoveryDay {

    private final LocalDate date;
    private final RecoverySource source;
    private final Integer recoveryScore;
    private final Double hrvMs;
    private final Double sleepHours;
    private final Integer soreness;
    private final Integer energy;

    private RecoveryDay(Builder builder) {
        if (builder.date == null) {
            throw new IllegalArgumentException(
                    "A recovery day needs a"
                    + " date");
        }
        if (builder.source == null) {
            throw new IllegalArgumentException(
                    "A recovery day needs a"
                    + " source");
        }
        this.date = builder.date;
        this.source = builder.source;
        this.recoveryScore =
                builder.recoveryScore;
        this.hrvMs = builder.hrvMs;
        this.sleepHours = builder.sleepHours;
        this.soreness = builder.soreness;
        this.energy = builder.energy;
    }

    public static Builder on(
            LocalDate date,
            RecoverySource source) {
        return new Builder(date, source);
    }

    public LocalDate date() {
        return date;
    }

    public RecoverySource source() {
        return source;
    }

    public OptionalInt recoveryScore() {
        return recoveryScore == null
                ? OptionalInt.empty()
                : OptionalInt.of(recoveryScore);
    }

    public OptionalDouble hrvMs() {
        return hrvMs == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(hrvMs);
    }

    public OptionalDouble sleepHours() {
        return sleepHours == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(sleepHours);
    }

    public OptionalInt soreness() {
        return soreness == null
                ? OptionalInt.empty()
                : OptionalInt.of(soreness);
    }

    public OptionalInt energy() {
        return energy == null
                ? OptionalInt.empty()
                : OptionalInt.of(energy);
    }

    /**
     * True when a wearable supplied a recovery
     * score, as opposed to the lifter reporting
     * how they feel.
     */
    public boolean hasObjectiveScore() {
        return recoveryScore != null;
    }

    /** True when nothing at all was recorded. */
    public boolean isEmpty() {
        return recoveryScore == null
                && hrvMs == null
                && sleepHours == null
                && soreness == null
                && energy == null;
    }

    @Override
    public String toString() {
        return date + " (" + source + ")";
    }

    /** Builds a day from whichever readings exist. */
    public static final class Builder {

        private final LocalDate date;
        private final RecoverySource source;
        private Integer recoveryScore;
        private Double hrvMs;
        private Double sleepHours;
        private Integer soreness;
        private Integer energy;

        private Builder(
                LocalDate date,
                RecoverySource source) {
            this.date = date;
            this.source = source;
        }

        public Builder recoveryScore(int score) {
            requireRange(score, 0, 100,
                    "Recovery score");
            this.recoveryScore = score;
            return this;
        }

        public Builder hrvMs(double hrv) {
            if (hrv < 0) {
                throw new IllegalArgumentException(
                        "HRV cannot be negative");
            }
            this.hrvMs = hrv;
            return this;
        }

        public Builder sleepHours(double hours) {
            if (hours < 0 || hours > 24) {
                throw new IllegalArgumentException(
                        "Sleep must be between 0"
                        + " and 24 hours");
            }
            this.sleepHours = hours;
            return this;
        }

        public Builder soreness(int rating) {
            requireRange(rating, 1, 5,
                    "Soreness");
            this.soreness = rating;
            return this;
        }

        public Builder energy(int rating) {
            requireRange(rating, 1, 5, "Energy");
            this.energy = rating;
            return this;
        }

        private static void requireRange(
                int value, int low, int high,
                String what) {
            if (value < low || value > high) {
                throw new IllegalArgumentException(
                        what + " must be between "
                        + low + " and " + high
                        + ", but was " + value);
            }
        }

        public RecoveryDay build() {
            return new RecoveryDay(this);
        }
    }
}
