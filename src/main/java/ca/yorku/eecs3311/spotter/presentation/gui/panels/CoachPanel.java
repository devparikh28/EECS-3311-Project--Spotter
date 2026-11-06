package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.util.List;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .ConversationHistory;
import ca.yorku.eecs3311.spotter.agent.agents
        .ChatAgent;
import ca.yorku.eecs3311.spotter.domain
        .ChatReply;
import ca.yorku.eecs3311.spotter.domain
        .MemoryNote;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;
import ca.yorku.eecs3311.spotter
        .presentation.support.ModelAccess;
import ca.yorku.eecs3311.spotter
        .presentation.support.PreparedChat;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * F11 on screen: the coach you can talk to.
 *
 * The eighth and last panel, and the one where
 * the design is least visible, because a chat
 * window looks the same whether or not anything
 * behind it is careful. So two things are kept on
 * screen that a chat window would usually hide:
 * which lookups the coach ran to answer, and what
 * it has remembered about the lifter, with a
 * button to make it forget.
 *
 * The memory being visible and erasable is not a
 * nicety. A coach that accumulates notes about
 * someone invisibly is a coach they cannot
 * correct, and the fastest way to lose trust in
 * one is to catch it believing something you
 * never said.
 */
public final class CoachPanel implements Panel {

    /** Notes shown beside the conversation. */
    public static final int NOTES_SHOWN = 12;

    private final GuiContext context;
    private final ConversationHistory history =
            new ConversationHistory();
    private final VBox root = new VBox(
            Cards.GAP);
    private final VBox transcript =
            new VBox(10);
    private final VBox lookups = new VBox(4);
    private final TextField question =
            new TextField();
    private final Button send =
            Cards.primary(
                new Button("Ask"));
    private final Label status =
            Cards.note("");

    public CoachPanel(GuiContext context) {
        this.context = context;
        question.setPromptText(
            "Ask your coach something. For"
            + " example: is my squat stalling,"
            + " or should I train today?");
        question.setOnAction(event -> ask());
        send.setOnAction(event -> ask());
        HBox.setHgrow(question,
                Priority.ALWAYS);
    }

    @Override
    public String title() {
        return "Coach";
    }

    @Override
    public String description() {
        return "Ask about your training. The"
            + " coach looks things up rather"
            + " than guessing.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();

        ScrollPane scroll =
                new ScrollPane(transcript);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(300);
        scroll.setStyle(
            "-fx-background-color: transparent;");

        if (transcript.getChildren().isEmpty()) {
            transcript.getChildren().add(
                Cards.note("Nothing asked yet."
                    + " The coach can see your"
                    + " logged training, your"
                    + " recovery, the block you"
                    + " are on and the food"
                    + " catalogue."));
        }

        VBox conversation = Cards.card(
            "CONVERSATION", scroll,
            new HBox(8, question, send),
            status);
        root.getChildren().add(conversation);

        if (!lookups.getChildren().isEmpty()) {
            root.getChildren().add(
                Cards.card("WHAT IT LOOKED UP",
                        lookups));
        }
        root.getChildren().add(memoryCard());
    }

    /** What the coach has kept, and a way out. */
    private Node memoryCard() {
        List<MemoryNote> notes = context
                .memory().recall(NOTES_SHOWN);

        VBox card = Cards.card(
                "WHAT IT REMEMBERS");
        if (notes.isEmpty()) {
            card.getChildren().add(
                Cards.note("Nothing yet. The"
                    + " coach keeps a fact only"
                    + " when you tell it"
                    + " something that would"
                    + " still matter weeks from"
                    + " now, and never its own"
                    + " advice."));
            return card;
        }

        for (MemoryNote note : notes) {
            Label line = Cards.styled(
                    note.content(), "mono");
            line.setWrapText(true);
            card.getChildren().add(line);
        }

        Button forget = new Button("Forget all"
                + " of this");
        forget.setOnAction(event -> {
            context.memory()
                    .forgetEverything();
            refresh();
        });
        card.getChildren().add(forget);
        return card;
    }

    private void ask() {
        String asked = question.getText();
        if (asked == null || asked.isBlank()) {
            return;
        }
        question.clear();
        send.setDisable(true);
        lookups.getChildren().clear();
        status.setText("Thinking...");

        addTurn("You", asked, "mono");
        history.said(asked);

        TrainingBlock block = context.blocks()
                .active().orElse(null);
        Session today = todaysSession(block);
        List<RecoveryFlag> flags = context
            .controller().recoveryFlags(
                LocalDate.now().minusWeeks(2));

        ModelAccess.Access access =
            ModelAccess.open(context.config(),
                false,
                PreparedChat.of(
                    context.controller()
                        .currentE1RMs(),
                    flags, today));

        ChatAgent agent = new ChatAgent(
            access.client(), context.prompts(),
            asked, history, context.memory(),
            context.profile().orElse(null),
            block,
            context.controller().workouts()
                .findAll(),
            context.controller().recovery()
                .findSince(LocalDate.now()
                    .minusWeeks(4)),
            context.controller().foods(),
            context.parser());

        context.tasks().run(agent::run,
            outcome -> {
                send.setDisable(false);
                status.setText(access.isLive()
                    ? ""
                    : access.explanation());
                show(outcome, agent);
            },
            failure -> {
                send.setDisable(false);
                status.setText("Something went"
                    + " wrong: "
                    + failure.getMessage());
            });
    }

    private Session todaysSession(
            TrainingBlock block) {
        if (block == null) {
            return null;
        }
        return block.allSessions().stream()
            .filter(each -> each.date()
                .equals(LocalDate.now()))
            .findFirst()
            .orElse(null);
    }

    private void show(
            AgentResult<ChatReply> outcome,
            ChatAgent agent) {

        if (!outcome.succeeded()) {
            addTurn("Coach",
                outcome.failureReason()
                    .orElse("I could not answer"
                        + " that."),
                "note");
            return;
        }

        ChatReply reply =
                outcome.value().orElseThrow();
        addTurn("Coach", reply.reply(), "note");
        history.replied(reply.reply());

        for (String used
                : outcome.trace()
                    .toolsCalled()) {
            lookups.getChildren().add(
                    Cards.styled(used, "mono"));
        }
        if (lookups.getChildren().isEmpty()) {
            lookups.getChildren().add(
                Cards.note("It answered without"
                    + " looking anything up."));
        }

        agent.remember(reply).ifPresent(kept ->
            status.setText("Remembered: "
                    + kept));
        refresh();
    }

    private void addTurn(String who,
                         String text,
                         String style) {
        if (transcript.getChildren().size() == 1
                && transcript.getChildren()
                    .get(0) instanceof Label
                    first
                && first.getStyleClass()
                    .contains("note")
                && first.getText().startsWith(
                        "Nothing asked")) {
            transcript.getChildren().clear();
        }

        Label speaker = Cards.styled(who,
                "card-heading");
        Label said = Cards.styled(text, style);
        said.setWrapText(true);
        transcript.getChildren().add(
                new VBox(2, speaker, said));
    }
}
