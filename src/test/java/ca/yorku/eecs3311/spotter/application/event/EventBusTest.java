package ca.yorku.eecs3311.spotter
        .application.event;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Observer.
 *
 * What matters about a bus is not that a message
 * arrives, which is one line of code, but what
 * happens at the edges: a listener that throws, a
 * listener added for several events, an event
 * nobody asked about. Those are the cases that
 * make a screen quietly stop updating.
 */
class EventBusTest {

    private final EventBus bus = new EventBus();

    @Test
    void aSubscriberIsTold() {
        List<SpotterEvent> heard =
                new ArrayList<>();

        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            heard::add);
        bus.publish(SpotterEvent.BLOCK_CHANGED);

        assertThat(heard).containsExactly(
            SpotterEvent.BLOCK_CHANGED);
    }

    @Test
    void onlyThoseWhoAskedAreTold() {
        List<SpotterEvent> heard =
                new ArrayList<>();

        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            heard::add);
        bus.publish(
            SpotterEvent.NUTRITION_CHANGED);

        assertThat(heard).isEmpty();
    }

    @Test
    void aListenerCanAskForSeveral() {
        List<SpotterEvent> heard =
                new ArrayList<>();

        bus.subscribeAll(
            Set.of(SpotterEvent.BLOCK_CHANGED,
                SpotterEvent.PROFILE_CHANGED),
            heard::add);
        bus.publish(SpotterEvent.BLOCK_CHANGED);
        bus.publish(
            SpotterEvent.PROFILE_CHANGED);
        bus.publish(
            SpotterEvent.NUTRITION_CHANGED);

        assertThat(heard).containsExactly(
            SpotterEvent.BLOCK_CHANGED,
            SpotterEvent.PROFILE_CHANGED);
    }

    @Test
    void everySubscriberIsToldInOrder() {
        List<String> order = new ArrayList<>();

        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            event -> order.add("first"));
        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            event -> order.add("second"));
        bus.publish(SpotterEvent.BLOCK_CHANGED);

        assertThat(order)
            .containsExactly("first", "second");
    }

    /**
     * The rule that matters. A panel that fails
     * to redraw is a bad panel; it should not
     * also become the reason every other screen
     * stops updating.
     */
    @Test
    @DisplayName(
        "a listener that throws does not stop the"
        + " others from being told")
    void oneBrokenListenerDoesNotStopTheRest() {
        List<String> told = new ArrayList<>();

        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            event -> {
                throw new IllegalStateException(
                        "panel is broken");
            });
        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            event -> told.add("still told"));

        bus.publish(SpotterEvent.BLOCK_CHANGED);

        assertThat(told)
            .containsExactly("still told");
        assertThat(bus.failures())
            .hasSize(1)
            .first()
            .extracting(
                RuntimeException::getMessage)
            .asString()
            .contains("broken");
    }

    @Test
    void anEventWithNoSubscribersIsHarmless() {
        bus.publish(SpotterEvent.BLOCK_CHANGED);

        assertThat(bus.failures()).isEmpty();
        assertThat(bus.subscriberCount(
            SpotterEvent.BLOCK_CHANGED))
            .isZero();
    }

    @Test
    void subscribingToNothingSubscribes() {
        bus.subscribeAll(Set.of(), event -> {
            throw new AssertionError(
                "should never be called");
        });

        for (SpotterEvent event
                : EnumSet.allOf(
                    SpotterEvent.class)) {
            bus.publish(event);
        }

        assertThat(bus.failures()).isEmpty();
    }

    @Test
    void subscribersAreCounted() {
        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            event -> { });
        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            event -> { });

        assertThat(bus.subscriberCount(
            SpotterEvent.BLOCK_CHANGED))
            .isEqualTo(2);
    }

    @Test
    void clearingForgetsEveryone() {
        List<SpotterEvent> heard =
                new ArrayList<>();
        bus.subscribe(
            SpotterEvent.BLOCK_CHANGED,
            heard::add);

        bus.clear();
        bus.publish(SpotterEvent.BLOCK_CHANGED);

        assertThat(heard).isEmpty();
    }

    @Test
    void anEventAndAListenerAreBothRequired() {
        assertThatThrownBy(() ->
            bus.subscribe(null, event -> { }))
            .isInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() ->
            bus.subscribe(
                SpotterEvent.BLOCK_CHANGED,
                null))
            .isInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() ->
            bus.publish(null))
            .isInstanceOf(
                IllegalArgumentException.class);
    }
}
