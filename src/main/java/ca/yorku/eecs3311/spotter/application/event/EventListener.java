package ca.yorku.eecs3311.spotter
        .application.event;

/**
 * Something that wants to be told.
 *
 * This is the Observer from the Stage 1 design.
 * It is one method rather than an abstract panel
 * class, so a listener can be a lambda and a
 * screen does not have to inherit from anything
 * to hear about a change.
 */
@FunctionalInterface
public interface EventListener {

    /**
     * Called when the event it subscribed to is
     * published.
     *
     * Called on whichever thread published, so a
     * listener that touches the screen is
     * responsible for getting back onto the user
     * interface thread.
     */
    void onEvent(SpotterEvent event);
}
