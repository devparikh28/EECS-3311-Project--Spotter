package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The small pieces every panel is built from.
 *
 * Static factories rather than a class hierarchy.
 * A card is a box with a heading and some
 * content; subclassing VBox to say so would add a
 * type without adding a thought, and these are
 * composed rather than extended.
 *
 * Nothing here names a colour or a size. Every
 * appearance decision is a style class resolved
 * in spotter.css, so the look can change without
 * a panel being recompiled.
 */
public final class Cards {

    /** Gap between cards and rows, in pixels. */
    public static final double GAP = 14;

    private Cards() {
    }

    /** A titled card holding whatever is given. */
    public static VBox card(String heading,
                            Node... content) {
        VBox box = new VBox(8);
        box.getStyleClass().add("card");
        if (heading != null
                && !heading.isBlank()) {
            box.getChildren().add(
                    styled(heading,
                            "card-heading"));
        }
        box.getChildren().addAll(content);
        return box;
    }

    /**
     * The one card on a screen that is louder
     * than the others.
     *
     * Loud on purpose and only when there is
     * something to act on: the accent treatment
     * means the lifter has training to do today,
     * and the quiet treatment means they do not.
     * A hero card that is always bright is
     * decoration; one that changes is
     * information.
     *
     * @param urgent whether to use the accent
     *               treatment
     */
    public static VBox hero(
            boolean urgent,
            String eyebrow,
            String figure,
            String caption,
            Node... extra) {

        VBox box = new VBox(2);
        box.getStyleClass().addAll("card",
            urgent ? "hero-urgent" : "hero-calm");

        if (eyebrow != null
                && !eyebrow.isBlank()) {
            box.getChildren().add(styled(
                eyebrow, "hero-eyebrow"));
        }
        if (figure != null
                && !figure.isBlank()) {
            Label big = styled(figure,
                    "hero-figure");
            big.setWrapText(true);
            box.getChildren().add(big);
        }

        if (caption != null
                && !caption.isBlank()) {
            Label said = styled(caption,
                    "hero-caption");
            said.setWrapText(true);
            box.getChildren().add(said);
        }
        box.getChildren().addAll(extra);
        return box;
    }

    /**
     * A card showing one number, which is what
     * most of the dashboard is.
     */
    public static VBox figure(
            String heading,
            String value,
            String unit) {
        Label number = styled(value, "figure");
        VBox box = card(heading, number);
        if (unit != null && !unit.isBlank()) {
            Label said = styled(unit,
                    "figure-unit");
            said.setWrapText(true);
            box.getChildren().add(said);
        }
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /**
     * A figure with a movement beside it.
     *
     * The delta carries its own colour, because
     * a number going the wrong way should be
     * visible without being read.
     */
    public static VBox stat(
            String heading,
            String value,
            String delta,
            Direction direction,
            String caption) {

        Label number = styled(value, "figure");
        VBox box = card(heading, number);

        if (delta != null && !delta.isBlank()) {
            box.getChildren().add(styled(delta,
                    direction.styleClass()));
        }
        if (caption != null
                && !caption.isBlank()) {
            Label said = styled(caption,
                    "figure-unit");
            said.setWrapText(true);
            box.getChildren().add(said);
        }
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Which way a figure has moved. */
    public enum Direction {
        /** Moving the way the lifter wants. */
        UP("delta-up"),
        /** Moving the wrong way. */
        DOWN("delta-down"),
        /** Moved too little to call. */
        FLAT("delta-flat");

        private final String styleClass;

        Direction(String styleClass) {
            this.styleClass = styleClass;
        }

        String styleClass() {
            return styleClass;
        }
    }

    /**
     * Marks the one action on a screen that
     * commits: keep the plan, save the profile,
     * log the meal. At most one per screen, or
     * the accent stops meaning anything.
     */
    public static javafx.scene.control.Button
            primary(
                javafx.scene.control.Button
                        button) {
        button.getStyleClass().add(
                "button-primary");
        return button;
    }

    /** A small coloured pill. */
    public static Label badge(String text,
                              String kind) {
        Label label = styled(text, "badge");
        label.getStyleClass().add(
                "badge-" + kind);
        return label;
    }

    /** A heading that separates groups of cards. */
    public static Label sectionHeader(
            String text) {
        return styled(text, "section-header");
    }

    /** A row of cards that share the width. */
    public static HBox row(Node... cards) {
        HBox box = new HBox(GAP, cards);
        for (Node card : cards) {
            HBox.setHgrow(card,
                    Priority.ALWAYS);
        }
        return box;
    }

    /**
     * A row where the first child takes the
     * space it needs and the rest sit beside it.
     */
    public static HBox split(Node main,
                             Node... beside) {
        HBox box = new HBox(GAP);
        box.getChildren().add(main);
        HBox.setHgrow(main, Priority.ALWAYS);
        box.getChildren().addAll(beside);
        return box;
    }

    /** Buttons in a line, with the usual gap. */
    public static HBox actions(Node... buttons) {
        HBox box = new HBox(8, buttons);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Something that eats the leftover width. */
    public static Region grow() {
        Region filler = new Region();
        HBox.setHgrow(filler, Priority.ALWAYS);
        return filler;
    }

    /** A column with the usual spacing. */
    public static VBox column(Node... children) {
        VBox box = new VBox(GAP, children);
        box.setPadding(new Insets(0));
        return box;
    }

    /** A label with a style class applied. */
    public static Label styled(String text,
                               String styleClass) {
        Label label = new Label(text == null
                ? ""
                : text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** A wrapped note, for explanations. */
    public static Label note(String text) {
        Label label = styled(text, "note");
        label.setWrapText(true);
        return label;
    }

    /**
     * What a panel shows when there is nothing
     * to show, which says what to do about it
     * rather than leaving an empty rectangle.
     */
    public static VBox emptyState(
            String explanation,
            String whatToDo) {
        Label said = styled(explanation,
                "empty-state");
        said.setWrapText(true);
        VBox box = card(null, said);
        if (whatToDo != null
                && !whatToDo.isBlank()) {
            Label next = styled(whatToDo,
                    "mono");
            box.getChildren().add(next);
        }
        return box;
    }

    /** A label pairing a name with a value. */
    public static HBox field(String name,
                             String value) {
        Label left = styled(name, "field-name");
        left.setMinWidth(150);
        Label right = styled(value == null
                ? "not set"
                : value, "field-value");
        right.setWrapText(true);
        HBox box = new HBox(8, left, right);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * A labelled bar, for a proportion the
     * lifter should be able to read at a glance
     * rather than compute from two numbers.
     */
    public static VBox meter(String heading,
                             String value,
                             double fraction) {
        double clamped = Math.max(0,
                Math.min(1, fraction));

        Region fill = new Region();
        fill.getStyleClass().add("meter-fill");
        fill.setMinHeight(8);
        fill.setPrefHeight(8);

        Region track = new Region();
        track.getStyleClass().add("meter-track");
        track.setMinHeight(8);
        track.setPrefHeight(8);
        HBox.setHgrow(track, Priority.ALWAYS);

        HBox bar = new HBox(fill, track);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.widthProperty().addListener(
            (observed, was, now) ->
                fill.setPrefWidth(
                    now.doubleValue() * clamped));

        VBox box = new VBox(6,
            styled(heading, "card-heading"),
            styled(value, "meter-value"),
            bar);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }
}
