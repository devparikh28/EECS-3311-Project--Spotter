package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A whole training block: weeks of sessions, each
 * of prescribed exercises.
 *
 * Produced either by the generation agent or by
 * adopting a programme the lifter already
 * follows, which is what origin records. Once it
 * exists both are treated the same way: tracked,
 * adjusted and progressed.
 */
public final class TrainingBlock {

    private final int id;
    private final LocalDate startDate;
    private final String progression;
    private final String splitName;
    private final BlockOrigin origin;
    private final List<Week> weeks;

    public TrainingBlock(
            int id,
            LocalDate startDate,
            String progression,
            String splitName,
            BlockOrigin origin,
            List<Week> weeks) {
        if (startDate == null) {
            throw new IllegalArgumentException(
                    "A block needs a start"
                    + " date");
        }
        this.id = id;
        this.startDate = startDate;
        this.progression = progression;
        this.splitName = splitName;
        this.origin = origin == null
                ? BlockOrigin.GENERATED
                : origin;
        this.weeks = new ArrayList<>(
                weeks == null
                    ? List.of()
                    : weeks);
    }

    public int id() {
        return id;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public Optional<String> progression() {
        return Optional.ofNullable(progression);
    }

    public Optional<String> splitName() {
        return Optional.ofNullable(splitName);
    }

    public BlockOrigin origin() {
        return origin;
    }

    public List<Week> weeks() {
        return Collections.unmodifiableList(
                weeks);
    }

    public int weekCount() {
        return weeks.size();
    }

    public Optional<Week> findWeek(int number) {
        for (Week week : weeks) {
            if (week.number() == number) {
                return Optional.of(week);
            }
        }
        return Optional.empty();
    }

    public Optional<Session> findSession(
            int sessionId) {
        for (Week week : weeks) {
            for (Session session
                    : week.sessions()) {
                if (session.id() == sessionId) {
                    return Optional.of(session);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a prescription anywhere in the block.
     * Edits arrive from the interface carrying
     * only a prescription id, so the block has to
     * be able to locate one without the caller
     * knowing which session it sits in.
     */
    public Optional<ExercisePrescription>
            findPrescription(
                    int prescriptionId) {
        for (Week week : weeks) {
            for (Session session
                    : week.sessions()) {
                Optional<ExercisePrescription>
                    found = session
                        .findPrescription(
                            prescriptionId);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** The session a prescription belongs to. */
    public Optional<Session> sessionContaining(
            int prescriptionId) {
        for (Week week : weeks) {
            for (Session session
                    : week.sessions()) {
                if (session.findPrescription(
                        prescriptionId)
                        .isPresent()) {
                    return Optional.of(session);
                }
            }
        }
        return Optional.empty();
    }

    public List<Session> allSessions() {
        List<Session> all = new ArrayList<>();
        for (Week week : weeks) {
            all.addAll(week.sessions());
        }
        return List.copyOf(all);
    }
}
