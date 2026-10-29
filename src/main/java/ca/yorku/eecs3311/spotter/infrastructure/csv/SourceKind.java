package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

/** The exports Spotter recognises by itself. */
public enum SourceKind {

    /** A Hevy workout export. */
    HEVY("Hevy workout export"),

    /** A Whoop physiological cycles export. */
    WHOOP("Whoop recovery export"),

    /** Recognised by nobody; needs a mapping. */
    UNKNOWN("an unrecognised format");

    private final String description;

    SourceKind(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
