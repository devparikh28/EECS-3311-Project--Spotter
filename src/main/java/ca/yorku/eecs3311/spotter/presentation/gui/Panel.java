package ca.yorku.eecs3311.spotter
        .presentation.gui;

import javafx.scene.Node;

/**
 * One screen in the dashboard.
 *
 * The shell knows only this. It puts the title in
 * the navigation, puts the node in the content
 * area and calls refresh when the panel is shown,
 * which means a new panel is one class and one
 * line in the shell rather than an edit to a
 * growing switch.
 */
public interface Panel {

    /** What the navigation calls this screen. */
    String title();

    /** One line under the title. */
    String description();

    /** The content, built once and reused. */
    Node node();

    /**
     * Reads whatever it shows again.
     *
     * Called every time the panel is opened,
     * because an import or a generated plan in
     * another panel changes what this one should
     * show, and a dashboard displaying stale
     * numbers is worse than one that is briefly
     * empty.
     */
    void refresh();
}
