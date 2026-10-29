package ca.yorku.eecs3311.spotter.domain;

/**
 * The three attempts for one lift at a meet.
 *
 * A meet gives three tries. The convention these
 * figures follow is: open with something certain,
 * take a small jump to a personal best, then
 * reach on the third.
 *
 * @param lift      which lift
 * @param opener    first attempt, in kilograms
 * @param second    second attempt
 * @param third     third attempt
 * @param rationale why these numbers
 */
public record MeetAttempts(
        Lift lift,
        double opener,
        double second,
        double third,
        String rationale) {

    public MeetAttempts {
        if (lift == null) {
            throw new IllegalArgumentException(
                    "Attempts need a lift");
        }
        if (opener <= 0) {
            throw new IllegalArgumentException(
                    "An opener must be above"
                    + " zero");
        }
        if (second < opener) {
            throw new IllegalArgumentException(
                    "A second attempt cannot be"
                    + " lighter than the opener");
        }
        if (third < second) {
            throw new IllegalArgumentException(
                    "A third attempt cannot be"
                    + " lighter than the second");
        }
        rationale = rationale == null
                ? ""
                : rationale;
    }

    /** The jump from opener to third. */
    public double totalJump() {
        return third - opener;
    }
}
