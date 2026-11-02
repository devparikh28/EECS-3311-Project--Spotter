package ca.yorku.eecs3311.spotter.agent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .ProfileDraft;

/**
 * The gate for onboarding.
 *
 * A separate class from PlanValidator on purpose.
 * That one exists to judge whether a prescribed
 * load is safe for this lifter, and nothing it
 * knows helps here; a validator that judged
 * loads, meals, explanations and now profiles
 * would be four unrelated responsibilities
 * sharing a name.
 *
 * What it checks is narrow. A draft may be
 * incomplete, which is a reason to ask the lifter
 * another question rather than a fault, so that
 * is reported as missing answers. What it rejects
 * is a draft stating something that cannot be
 * true, or a meet already in the past, because
 * those mean the free text was misread.
 */
public final class ProfileValidator {

    /**
     * How far ahead a meet may be before it is
     * more likely a misreading than a plan.
     */
    public static final int MAX_MEET_YEARS = 3;

    /**
     * Checks a draft.
     *
     * @param draft what the model read out of
     *              the lifter's own words
     * @param today the date to judge a meet
     *              against
     * @return empty when acceptable, otherwise
     *         the reason, written for the model
     */
    public Optional<String> validate(
            ProfileDraft draft,
            LocalDate today) {

        if (draft == null) {
            return Optional.of(
                "No profile was produced.");
        }

        List<String> implausible =
                draft.implausible();
        if (!implausible.isEmpty()) {
            return Optional.of(
                "This cannot be right: "
                + String.join("; ", implausible)
                + ". Read the lifter's own words"
                + " again and do not infer"
                + " anything they did not say.");
        }

        Optional<String> meetProblem =
                checkMeetDate(draft, today);
        if (meetProblem.isPresent()) {
            return meetProblem;
        }

        if (!draft.isComplete()) {
            return Optional.of(
                "The lifter has not said "
                + String.join(", ",
                        draft.missing())
                + ". Do not guess any of it."
                + " Leave those fields out so"
                + " they can be asked.");
        }
        return Optional.empty();
    }

    private Optional<String> checkMeetDate(
            ProfileDraft draft,
            LocalDate today) {

        if (draft.meetDate().isEmpty()
                || today == null) {
            return Optional.empty();
        }
        LocalDate meet = draft.meetDate().get();

        if (meet.isBefore(today)) {
            return Optional.of("The meet date "
                + meet + " has already passed."
                + " Either read the year again"
                + " or leave the meet date"
                + " out.");
        }
        if (meet.isAfter(today.plusYears(
                MAX_MEET_YEARS))) {
            return Optional.of("A meet on "
                + meet + " is more than "
                + MAX_MEET_YEARS + " years away,"
                + " which is more likely a"
                + " misreading than a plan."
                + " Check the year.");
        }
        return Optional.empty();
    }

    /**
     * True when the only thing wrong is that
     * answers are missing, which the caller
     * handles by asking rather than by retrying
     * the model.
     */
    public boolean onlyNeedsMoreAnswers(
            ProfileDraft draft,
            LocalDate today) {
        return draft != null
            && draft.implausible().isEmpty()
            && checkMeetDate(draft, today)
                .isEmpty()
            && !draft.isComplete();
    }
}
