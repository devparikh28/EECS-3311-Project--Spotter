package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One planned training day.
 *
 * The prescription list is mutable because the
 * plan is edited: a session is adjusted for poor
 * recovery, an exercise is substituted, loads are
 * progressed. Every such edit goes through a
 * command so it can be undone.
 */
public final class Session {

    private final int id;
    private final LocalDate date;
    private final String focus;
    private final List<ExercisePrescription>
            prescriptions;
    private String adjustmentNote;

    public Session(
            int id,
            LocalDate date,
            String focus,
            List<ExercisePrescription>
                    prescriptions) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "A session needs a date");
        }
        this.id = id;
        this.date = date;
        this.focus = focus;
        this.prescriptions = new ArrayList<>(
                prescriptions == null
                    ? List.of()
                    : prescriptions);
    }

    public int id() {
        return id;
    }

    public LocalDate date() {
        return date;
    }

    public Optional<String> focus() {
        return Optional.ofNullable(focus);
    }

    public List<ExercisePrescription>
            prescriptions() {
        return Collections.unmodifiableList(
                prescriptions);
    }

    /** Why this session was changed, if it was. */
    public Optional<String> adjustmentNote() {
        return Optional.ofNullable(
                adjustmentNote);
    }

    public void setAdjustmentNote(String note) {
        this.adjustmentNote = note;
    }

    public boolean wasAdjusted() {
        return adjustmentNote != null;
    }

    public Optional<ExercisePrescription>
            findPrescription(
                    int prescriptionId) {
        for (ExercisePrescription p
                : prescriptions) {
            if (p.id() == prescriptionId) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Swaps one prescription for another with the
     * same id, keeping its position in the
     * session.
     *
     * @return the prescription that was replaced
     */
    public Optional<ExercisePrescription>
            replacePrescription(
                ExercisePrescription
                        replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException(
                    "A replacement is required");
        }
        for (int i = 0;
                i < prescriptions.size(); i++) {
            if (prescriptions.get(i).id()
                    == replacement.id()) {
                return Optional.of(
                    prescriptions.set(i,
                            replacement));
            }
        }
        return Optional.empty();
    }

    /** Total planned weight for the session. */
    public double plannedTonnage() {
        double total = 0;
        for (ExercisePrescription p
                : prescriptions) {
            total += p.plannedTonnage();
        }
        return total;
    }

    public boolean isEmpty() {
        return prescriptions.isEmpty();
    }

    @Override
    public String toString() {
        return date + (focus == null
                ? ""
                : " " + focus);
    }
}
