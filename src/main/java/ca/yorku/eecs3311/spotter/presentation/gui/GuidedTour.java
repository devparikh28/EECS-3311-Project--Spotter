package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * A guided tour of the dashboard.
 *
 * A caption card sits over the bottom right of the
 * window, says what the screen being shown is for,
 * and moves on by itself. Each step runs an action
 * (normally "open this screen") and then waits long
 * enough to read the text, with a thin bar showing
 * how long is left.
 *
 * It exists for two audiences: someone opening
 * Spotter for the first time, and anyone watching a
 * screen recording with the sound off. In both cases
 * the screen should explain itself.
 *
 * The tour only looks. It never saves, imports or
 * calls the model, so running it changes no data.
 */
public final class GuidedTour {

    /** One caption and what to show while it is up. */
    public record Step(String tag, String text,
                       Runnable action) {

        /** Reading time: a floor plus text length. */
        double seconds() {
            return Math.max(6.0,
                    3.0 + text.length() / 13.0);
        }
    }

    private final List<Step> steps;
    private final Runnable onEnd;
    private final VBox card = new VBox(8);
    private final Label tag =
            new Label();
    private final Label count =
            new Label();
    private final Label text = new Label();
    private final ProgressBar progress =
            new ProgressBar(0);
    private Timeline clock;
    private int index = -1;
    private boolean running;

    public GuidedTour(List<Step> steps,
                      Runnable onEnd) {
        this.steps = List.copyOf(steps);
        this.onEnd = onEnd;

        tag.getStyleClass().add("tour-tag");
        count.getStyleClass().add("tour-count");
        text.getStyleClass().add("tour-text");
        text.setWrapText(true);
        progress.getStyleClass().add(
                "tour-progress");
        progress.setMaxWidth(Double.MAX_VALUE);

        Button skip = new Button("Skip");
        skip.getStyleClass().add("tour-button");
        skip.setOnAction(event -> next());
        Button stop = new Button("End tour");
        stop.getStyleClass().add("tour-button");
        stop.setOnAction(event -> stop());

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox head = new HBox(10, tag, count,
                gap, skip, stop);
        head.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().setAll(head, text,
                progress);
        card.getStyleClass().add("tour-caption");
        card.setMaxWidth(620);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setVisible(false);
        card.setManaged(false);
        StackPane.setAlignment(card,
                Pos.BOTTOM_RIGHT);
        StackPane.setMargin(card,
                new Insets(0, 24, 24, 0));
    }

    /** The caption card, to be placed in a StackPane. */
    public VBox node() {
        return card;
    }

    public boolean running() {
        return running;
    }

    /** Starts from the first step. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        index = -1;
        card.setVisible(true);
        card.setManaged(true);
        next();
    }

    /** Ends the tour wherever it has got to. */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (clock != null) {
            clock.stop();
        }
        card.setVisible(false);
        card.setManaged(false);
        onEnd.run();
    }

    private void next() {
        if (!running) {
            return;
        }
        if (clock != null) {
            clock.stop();
        }
        index++;
        if (index >= steps.size()) {
            stop();
            return;
        }
        Step step = steps.get(index);
        step.action().run();
        tag.setText(step.tag());
        count.setText((index + 1) + " of "
                + steps.size());
        text.setText(step.text());

        progress.setProgress(0);
        clock = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(
                    progress.progressProperty(),
                    0)),
            new KeyFrame(Duration.seconds(
                    step.seconds()),
                new KeyValue(
                    progress.progressProperty(),
                    1)));
        clock.setOnFinished(event -> next());
        clock.play();
    }
}
