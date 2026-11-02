package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDateTime;

/**
 * One thing worth remembering about the lifter.
 *
 * Notes exist because a coach who forgets is not
 * a coach. A lifter who mentions in March that
 * their left shoulder dislikes flat benching
 * should not have to mention it again in April,
 * and nothing else in Spotter's storage is shaped
 * to hold a sentence like that: it is not a
 * limitation with patterns attached, not a
 * profile field, just something said once that
 * ought to carry forward.
 *
 * Deliberately plain. A note is text and a time,
 * and the restraint is the point: the moment a
 * note gains a category or a confidence score,
 * something has to decide those, and that
 * something would be the model deciding what it
 * knows about you.
 *
 * @param id        assigned by storage
 * @param createdAt when it was first recorded
 * @param content   the note itself
 */
public record MemoryNote(
        int id,
        LocalDateTime createdAt,
        String content) {

    /** Longest note worth keeping. */
    public static final int MAX_LENGTH = 300;

    public MemoryNote {
        if (content == null
                || content.isBlank()) {
            throw new IllegalArgumentException(
                    "A note needs content");
        }
        content = content.strip();
        if (content.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "A note must be under "
                + MAX_LENGTH + " characters,"
                + " but was "
                + content.length()
                + ". Notes are facts, not"
                + " transcripts.");
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** A new note, not yet stored. */
    public static MemoryNote of(String content) {
        return new MemoryNote(0,
                LocalDateTime.now(), content);
    }

    /**
     * True when this says the same thing as
     * another, ignoring case and surrounding
     * space. Used to keep the same fact from
     * being recorded every time it comes up in
     * conversation.
     */
    public boolean saysTheSameAs(
            MemoryNote other) {
        return other != null
            && content.equalsIgnoreCase(
                    other.content());
    }
}
