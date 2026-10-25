package ca.yorku.eecs3311.spotter.domain;

/**
 * The unit the lifter reads weights in. Every
 * weight is stored in kilograms; this only
 * affects display and CSV import.
 */
public enum WeightUnit {

    KG(1.0),
    LB(0.45359237);

    private final double toKilograms;

    WeightUnit(double toKilograms) {
        this.toKilograms = toKilograms;
    }

    /** Converts a value in this unit to kg. */
    public double toKg(double value) {
        return value * toKilograms;
    }

    /** Converts kilograms to this unit. */
    public double fromKg(double kilograms) {
        return kilograms / toKilograms;
    }
}
