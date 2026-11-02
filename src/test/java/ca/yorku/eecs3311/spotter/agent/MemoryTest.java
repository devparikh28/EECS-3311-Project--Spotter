package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.domain
        .MemoryNote;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .MemoryRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteMemoryRepository;

/**
 * Tests for what the coach remembers and what it
 * is not allowed to.
 *
 * The refusals carry the weight. A coach that
 * remembers its own suggestion as though the
 * lifter had said it will, a few conversations
 * later, be certain they asked for something they
 * never mentioned, and nothing downstream can
 * detect that once the note is stored.
 */
class MemoryTest {

    private Database database;
    private MemoryRepository notes;
    private MemoryManager memory;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        notes = new SqliteMemoryRepository(
                database);
        memory = new MemoryManager(notes);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName(
        "something the lifter said is kept")
    void aStatedFactIsKept() {
        assertThat(memory.remember(
            "trains at a commercial gym before"
            + " work, so mornings only"))
            .isPresent();

        assertThat(notes.count()).isEqualTo(1);
        assertThat(memory.describe(10))
            .contains("mornings only");
    }

    @Test
    @DisplayName(
        "notes come back newest first, since"
        + " that is the only order ever wanted")
    void notesComeBackNewestFirst() {
        memory.remember("competes in the 83 kg"
                + " class");
        memory.remember("travels for work most"
                + " Thursdays");

        assertThat(memory.recall(10))
            .extracting(MemoryNote::content)
            .containsExactly(
                "travels for work most"
                    + " Thursdays",
                "competes in the 83 kg class");
    }

    @Test
    @DisplayName(
        "the same fact is not recorded twice")
    void duplicatesAreRefused() {
        memory.remember("competes in the 83 kg"
                + " class");

        assertThat(memory.remember(
            "Competes in the 83 kg class"))
            .isEmpty();
        assertThat(memory.refusal()
                .orElseThrow())
            .contains("already remembered");
        assertThat(notes.count()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "the coach's own reasoning is refused,"
        + " so it cannot later be mistaken for"
        + " the lifter's words")
    void coachVoiceIsRefused() {
        assertThat(memory.remember(
            "I suggested dropping the volume"
            + " for a week"))
            .isEmpty();
        assertThat(memory.refusal()
                .orElseThrow())
            .contains("coach's own reasoning");

        assertThat(memory.remember(
            "The lifter said I think the squat"
            + " is stalling"))
            .isEmpty();

        assertThat(notes.count()).isZero();
    }

    @Test
    @DisplayName(
        "a fragment is refused as too short to"
        + " be a fact")
    void fragmentsAreRefused() {
        assertThat(memory.remember("squat"))
            .isEmpty();
        assertThat(memory.refusal()
                .orElseThrow())
            .contains("too short");
    }

    @Test
    @DisplayName(
        "a summary is refused as too long to be"
        + " a fact")
    void summariesAreRefused() {
        assertThat(memory.remember(
            "and so on, ".repeat(40)))
            .isEmpty();
        assertThat(memory.refusal()
                .orElseThrow())
            .contains("summary rather than a"
                    + " fact");
    }

    @Test
    @DisplayName(
        "a note too long for the domain is"
        + " refused before it reaches storage")
    void theDomainRefusesLongNotes() {
        try {
            MemoryNote.of("x".repeat(400));
            assertThat(false).isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                .contains("facts, not"
                        + " transcripts");
        }
    }

    @Test
    @DisplayName(
        "the oldest notes fall off once the cap"
        + " is passed")
    void theOldestFallOff() {
        for (int i = 0;
                i < MemoryManager.MAX_NOTES + 5;
                i++) {
            memory.remember(
                "fact number " + i
                + " about this lifter");
        }

        assertThat(notes.count())
            .isEqualTo(MemoryManager.MAX_NOTES);
        assertThat(memory.describe(
                MemoryManager.MAX_NOTES))
            .contains("fact number 44")
            .doesNotContain("fact number 0 ");
    }

    @Test
    @DisplayName(
        "a lifter can tell the coach to forget"
        + " everything")
    void everythingCanBeForgotten() {
        memory.remember("competes in the 83 kg"
                + " class");
        memory.remember("travels most"
                + " Thursdays");

        memory.forgetEverything();

        assertThat(notes.count()).isZero();
        assertThat(memory.describe(10))
            .isEmpty();
    }

    @Test
    @DisplayName(
        "one note can be removed without"
        + " touching the rest")
    void oneNoteCanBeRemoved() {
        MemoryNote first = memory.remember(
            "competes in the 83 kg class")
            .orElseThrow();
        memory.remember("travels most"
                + " Thursdays");

        notes.delete(first.id());

        assertThat(notes.count()).isEqualTo(1);
        assertThat(memory.describe(10))
            .contains("Thursdays");
    }

    @Test
    @DisplayName(
        "a conversation keeps what was said, in"
        + " order")
    void aConversationKeepsItsTurns() {
        ConversationHistory history =
                new ConversationHistory();
        history.said("is my squat stalling?")
               .replied("it has gone up 7.5 kg"
                    + " in eight weeks")
               .said("what should I do?");

        assertThat(history.size()).isEqualTo(3);
        assertThat(history.lastQuestion()
                .orElseThrow())
            .isEqualTo("what should I do?");
        assertThat(history.transcript())
            .contains("Lifter: is my squat"
                    + " stalling?")
            .contains("Coach: it has gone up");
    }

    @Test
    @DisplayName(
        "the oldest turns fall off, since every"
        + " turn is sent again on the next"
        + " message")
    void theConversationIsCapped() {
        ConversationHistory history =
                new ConversationHistory(4);
        history.said("first")
               .replied("one")
               .said("second")
               .replied("two")
               .said("third");

        assertThat(history.size()).isEqualTo(4);
        assertThat(history.transcript())
            .doesNotContain("first")
            .contains("third");
    }

    @Test
    @DisplayName(
        "an empty conversation has no last"
        + " question rather than a null one")
    void anEmptyConversationIsSafe() {
        ConversationHistory history =
                new ConversationHistory();

        assertThat(history.isEmpty()).isTrue();
        assertThat(history.lastQuestion())
            .isEmpty();
        assertThat(history.transcript())
            .isEmpty();
    }
}
