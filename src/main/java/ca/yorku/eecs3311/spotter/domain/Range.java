package ca.yorku.eecs3311.spotter.domain;

/**
 * An acceptable load range in kilograms.
 *
 * A progression strategy produces one of these
 * and PlanValidator checks the model's proposed
 * load against it. That is the gate that stops a
 * generated plan prescribing a weight the lifter
 * cannot handle.
 *
 * @param minKg lowest acceptable load
 * @param maxKg highest acceptable load
 */
public record Range(
        double minKg,
        double maxKg) {

    public Range {
        if (minKg < 0) {
            throw new IllegalArgumentException(
                    "A load range cannot start"
                    + " below zero");
        }
        if (maxKg < minKg) {
            throw new IllegalArgumentException(
                    "A load range cannot end"
                    + " below where it starts");
        }
    }

    /**
     * Smallest difference worth treating as a
     * difference, in kilograms.
     *
     * Well under the lightest plate anyone owns.
     * Without it a load of 66.0 kg could be
     * refused against a range printed as 66.0 to
     * 76.7, because the bound was 66.04 and
     * rounding hid it. A rejection a lifter
     * cannot see the sense of is worse than a
     * hundredth of a kilo of slack.
     */
    public static final double TOLERANCE = 0.05;

    /** True when this load is acceptable. */
    public boolean contains(double kg) {
        return kg >= minKg - TOLERANCE
            && kg <= maxKg + TOLERANCE;
    }

    /** The middle of the range. */
    public double midpoint() {
        return (minKg + maxKg) / 2.0;
    }

    /** How wide the range is. */
    public double width() {
        return maxKg - minKg;
    }
}
