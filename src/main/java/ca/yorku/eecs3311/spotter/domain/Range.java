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

    /** True when this load is acceptable. */
    public boolean contains(double kg) {
        return kg >= minKg && kg <= maxKg;
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
