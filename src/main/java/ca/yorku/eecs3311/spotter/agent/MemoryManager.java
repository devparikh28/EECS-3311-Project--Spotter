package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .MemoryNote;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .MemoryRepository;

/**
 * Decides what the coach is allowed to remember.
 *
 * The chat agent may propose a note at the end of
 * a conversation, and this is the gate it passes
 * through, in the same spirit as the plan
 * validator: a model proposes, deterministic code
 * disposes. Without it, a coach would accumulate
 * whatever it felt like recording, including its
 * own guesses about the lifter, and those would
 * come back as facts in every later conversation.
 *
 * The rules are narrow and mechanical. A note
 * must be short enough to be a fact rather than a
 * transcript, must not repeat something already
 * held, and must not be advice the coach gave
 * itself. The last of those matters most: a model
 * that remembers its own suggestion as though the
 * lifter had said it will, three conversations
 * later, be certain the lifter asked for
 * something they never mentioned.
 */
public final class MemoryManager {

    /** Notes kept before the oldest is dropped. */
    public static final int MAX_NOTES = 40;

    /** Shortest note worth storing. */
    public static final int MIN_LENGTH = 10;

    /**
     * Openings that mark a note as the coach's
     * own reasoning rather than the lifter's
     * words.
     */
    private static final String[] COACH_VOICE = {
        "i suggested", "i recommended",
        "i told", "i advised", "i think",
        "i believe", "we should", "you should",
        "the plan i", "i generated",
        "i created", "i decided"};

    private final MemoryRepository notes;

    public MemoryManager(
            MemoryRepository notes) {
        if (notes == null) {
            throw new IllegalArgumentException(
                "A memory repository is"
                + " required");
        }
        this.notes = notes;
    }

    /**
     * Records a note, if it passes.
     *
     * @return the stored note, or empty with the
     *         reason available from refusal
     */
    public Optional<MemoryNote> remember(
            String content) {
        lastRefusal = null;

        if (content == null
                || content.isBlank()) {
            lastRefusal = "there was nothing to"
                    + " remember";
            return Optional.empty();
        }
        String text = content.strip();

        if (text.length() < MIN_LENGTH) {
            lastRefusal = "\"" + text + "\" is"
                + " too short to be a fact worth"
                + " keeping";
            return Optional.empty();
        }
        if (text.length()
                > MemoryNote.MAX_LENGTH) {
            lastRefusal = "that is "
                + text.length()
                + " characters, which is a"
                + " summary rather than a fact";
            return Optional.empty();
        }
        if (isCoachVoice(text)) {
            lastRefusal = "that is the coach's"
                + " own reasoning rather than"
                + " something the lifter said";
            return Optional.empty();
        }

        MemoryNote proposed =
                MemoryNote.of(text);
        for (MemoryNote held : notes.findAll()) {
            if (held.saysTheSameAs(proposed)) {
                lastRefusal = "that is already"
                        + " remembered";
                return Optional.empty();
            }
        }

        MemoryNote saved = notes.save(proposed);
        evictIfFull();
        return Optional.of(saved);
    }

    private String lastRefusal;

    /** Why the last note was not kept, if it was not. */
    public Optional<String> refusal() {
        return Optional.ofNullable(lastRefusal);
    }

    /**
     * True when a note is written in the coach's
     * voice. Crude on purpose: the cost of
     * refusing a borderline note is that the
     * lifter repeats themselves once, and the
     * cost of keeping one is a coach that
     * believes its own suggestions were
     * instructions.
     */
    private static boolean isCoachVoice(
            String text) {
        String lower = text.toLowerCase(
                java.util.Locale.ROOT);
        for (String opening : COACH_VOICE) {
            if (lower.startsWith(opening)
                    || lower.contains(" "
                        + opening)) {
                return true;
            }
        }
        return false;
    }

    /** Drops the oldest once the cap is passed. */
    private void evictIfFull() {
        List<MemoryNote> held =
                notes.findAll();
        if (held.size() <= MAX_NOTES) {
            return;
        }
        // findAll is newest first, so the tail
        // is what falls off.
        for (int i = MAX_NOTES;
                i < held.size(); i++) {
            notes.delete(held.get(i).id());
        }
    }

    /**
     * Whether a note would be kept, without
     * keeping it.
     *
     * The chat agent's validator asks this so a
     * note written in the coach's own voice is
     * sent back for correction rather than
     * silently dropped after the lifter has
     * already been given the answer.
     *
     * @return empty when it would be kept,
     *         otherwise the reason it would not
     */
    public Optional<String> wouldRefuse(
            String content) {
        if (content == null
                || content.isBlank()) {
            return Optional.empty();
        }
        String text = content.strip();

        if (text.length() < MIN_LENGTH) {
            return Optional.of("\"" + text
                + "\" is too short to be a fact"
                + " worth keeping");
        }
        if (text.length()
                > MemoryNote.MAX_LENGTH) {
            return Optional.of("that note is "
                + text.length() + " characters,"
                + " which is a summary rather"
                + " than a fact");
        }
        if (isCoachVoice(text)) {
            return Optional.of("that note is"
                + " your own reasoning rather"
                + " than something the lifter"
                + " said. Only record what they"
                + " told you.");
        }
        return Optional.empty();
    }

    /**
     * What to put in front of the model, newest
     * first.
     */
    public List<MemoryNote> recall(int limit) {
        return notes.recent(limit);
    }

    /** The notes as lines for a prompt. */
    public String describe(int limit) {
        List<String> lines = new ArrayList<>();
        for (MemoryNote note : recall(limit)) {
            lines.add("  " + note.content());
        }
        return String.join("\n", lines);
    }

    /** Forgets everything, when asked to. */
    public void forgetEverything() {
        notes.clear();
    }
}
