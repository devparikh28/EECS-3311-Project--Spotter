package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What has been said in this conversation.
 *
 * Held in memory and not stored. A conversation
 * is the short term thing: it matters until the
 * lifter closes the window, and anything in it
 * worth keeping becomes a note instead. Writing
 * every exchange to the database would be a
 * transcript, which is a different feature with
 * different consequences and one nobody asked
 * for.
 *
 * The cap is the reason this is a class rather
 * than a list. Every turn is sent to the model
 * again on the next message, so an uncapped
 * history is a bill that grows with the
 * conversation and eventually a request too large
 * to send. Oldest turns fall off the front.
 */
public final class ConversationHistory {

    /** Turns kept before the oldest fall off. */
    public static final int MAX_TURNS = 20;

    /** Who said something. */
    public enum Speaker {
        LIFTER,
        COACH
    }

    /** One thing said, by one party. */
    public record Turn(Speaker speaker,
                       String text) {

        public Turn {
            if (speaker == null) {
                throw new IllegalArgumentException(
                    "A turn needs a speaker");
            }
            if (text == null
                    || text.isBlank()) {
                throw new IllegalArgumentException(
                    "A turn needs something"
                    + " said");
            }
            text = text.strip();
        }
    }

    private final Deque<Turn> turns =
            new ArrayDeque<>();
    private final int maxTurns;

    public ConversationHistory() {
        this(MAX_TURNS);
    }

    public ConversationHistory(int maxTurns) {
        if (maxTurns < 2) {
            throw new IllegalArgumentException(
                "A conversation needs room for"
                + " at least a question and an"
                + " answer");
        }
        this.maxTurns = maxTurns;
    }

    public ConversationHistory said(
            String text) {
        return add(Speaker.LIFTER, text);
    }

    public ConversationHistory replied(
            String text) {
        return add(Speaker.COACH, text);
    }

    private ConversationHistory add(
            Speaker speaker, String text) {
        turns.addLast(new Turn(speaker, text));
        while (turns.size() > maxTurns) {
            turns.removeFirst();
        }
        return this;
    }

    public List<Turn> turns() {
        return List.copyOf(turns);
    }

    public int size() {
        return turns.size();
    }

    public boolean isEmpty() {
        return turns.isEmpty();
    }

    public void clear() {
        turns.clear();
    }

    /** The last thing the lifter said, if any. */
    public java.util.Optional<String>
            lastQuestion() {
        Turn[] all = turns.toArray(new Turn[0]);
        for (int i = all.length - 1; i >= 0;
                i--) {
            if (all[i].speaker()
                    == Speaker.LIFTER) {
                return java.util.Optional.of(
                        all[i].text());
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * The conversation as the model should read
     * it.
     *
     * Plain labelled lines rather than a
     * structured message list, because the
     * client's own interface takes one prompt and
     * the alternative would be leaking a
     * particular library's message format into
     * every agent.
     */
    public String transcript() {
        List<String> lines = new ArrayList<>();
        for (Turn turn : turns) {
            lines.add((turn.speaker()
                    == Speaker.LIFTER
                        ? "Lifter: "
                        : "Coach: ")
                + turn.text());
        }
        return String.join("\n", lines);
    }
}
