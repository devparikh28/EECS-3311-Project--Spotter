package ca.yorku.eecs3311.spotter.domain;

import java.util.Optional;

/**
 * What the coach says back, and anything it
 * thinks is worth remembering.
 *
 * The two travel together because they come from
 * one exchange, but they are kept apart because
 * they are judged differently. The reply is shown
 * to the lifter immediately; the note is offered
 * to the memory gate and may well be refused. A
 * refused note must not cost the lifter their
 * answer.
 *
 * @param reply    what to show the lifter
 * @param remember a fact worth keeping, or null
 */
public record ChatReply(
        String reply,
        String remember) {

    /** Longest answer worth showing in a chat. */
    public static final int MAX_REPLY = 1200;

    public ChatReply {
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException(
                    "A reply needs something in"
                    + " it");
        }
        reply = reply.strip();
        if (remember != null
                && remember.isBlank()) {
            remember = null;
        }
        if (remember != null) {
            remember = remember.strip();
        }
    }

    public Optional<String> note() {
        return Optional.ofNullable(remember);
    }

    public boolean isTooLong() {
        return reply.length() > MAX_REPLY;
    }
}
