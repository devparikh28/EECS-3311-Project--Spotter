package ca.yorku.eecs3311.spotter.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One week of a training block. */
public final class Week {

    private final int number;
    private final List<Session> sessions;

    public Week(int number,
                List<Session> sessions) {
        if (number < 1) {
            throw new IllegalArgumentException(
                    "Weeks are numbered from 1");
        }
        this.number = number;
        this.sessions = new ArrayList<>(
                sessions == null
                    ? List.of()
                    : sessions);
    }

    public int number() {
        return number;
    }

    public List<Session> sessions() {
        return Collections.unmodifiableList(
                sessions);
    }

    public double plannedTonnage() {
        double total = 0;
        for (Session session : sessions) {
            total += session.plannedTonnage();
        }
        return total;
    }
}
