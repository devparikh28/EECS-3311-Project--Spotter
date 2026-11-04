package ca.yorku.eecs3311.spotter
        .application.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Who to tell when something changes.
 *
 * The Subject half of the Observer from the Stage
 * 1 design. A screen that saves a plan says so
 * here rather than naming the screens that show
 * plans, which is the whole point: the Plan
 * screen and the Today screen do not know about
 * each other, and a new screen that shows plans
 * is one subscription rather than an edit to
 * every screen that could change one.
 *
 * Delivery is synchronous and in subscription
 * order, and a listener that throws does not stop
 * the rest from being told. That last rule
 * matters more than it looks: a panel failing to
 * redraw is a bad panel, and it should not also
 * become a panel that silently stops every other
 * screen from updating.
 */
public final class EventBus {

    private final Map<SpotterEvent,
            List<EventListener>> listeners =
                new EnumMap<>(SpotterEvent.class);
    private final List<RuntimeException>
            failures = new ArrayList<>();

    /** Subscribes to one kind of change. */
    public void subscribe(SpotterEvent event,
                          EventListener
                                  listener) {
        if (event == null
                || listener == null) {
            throw new IllegalArgumentException(
                "An event and a listener are"
                + " both required");
        }
        listeners.computeIfAbsent(event,
                key -> new ArrayList<>())
            .add(listener);
    }

    /**
     * Subscribes to several at once.
     *
     * Named apart from the single event version
     * rather than overloaded, because the two
     * are ambiguous for a caller passing a bare
     * null and the compiler is right to say so.
     *
     * An empty collection subscribes to nothing,
     * which is how a screen says it shows only
     * what it was given.
     */
    public void subscribeAll(
            Collection<SpotterEvent> events,
            EventListener listener) {
        if (events == null) {
            throw new IllegalArgumentException(
                "A set of events is required");
        }
        for (SpotterEvent event : events) {
            subscribe(event, listener);
        }
    }

    /** Tells everyone who asked about this. */
    public void publish(SpotterEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "An event is required");
        }
        List<EventListener> told = listeners
                .get(event);
        if (told == null) {
            return;
        }
        for (EventListener listener
                : List.copyOf(told)) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException broken) {
                failures.add(broken);
            }
        }
    }

    /** How many are listening for this. */
    public int subscriberCount(
            SpotterEvent event) {
        List<EventListener> told = listeners
                .get(event);
        return told == null ? 0 : told.size();
    }

    /**
     * Listeners that threw, for a test or a log.
     *
     * Swallowing a failure silently would make a
     * panel that stopped updating impossible to
     * explain, so the failures are kept rather
     * than discarded.
     */
    public List<RuntimeException> failures() {
        return List.copyOf(failures);
    }

    /** Forgets every subscription. */
    public void clear() {
        listeners.clear();
        failures.clear();
    }
}
