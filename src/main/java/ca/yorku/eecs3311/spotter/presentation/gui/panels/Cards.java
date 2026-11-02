package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The small pieces every panel is built from.
 *
 * Static factories rather than a class hierarchy.
 * A card is a box with a heading and some
 * content; subclassing VBox to say so would add a
 * type without adding a thought, and these are
 * composed rather than extended.
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
            box.getChildren().add(
                styled(unit, "figure-unit"));
        }
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
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
        Label left = styled(name, "card-heading");
        left.setMinWidth(150);
        Label right = new Label(value == null
                ? "not set"
                : value);
        return new HBox(8, left, right);
    }
}
