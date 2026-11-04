package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.util.Set;

import ca.yorku.eecs3311.spotter.application
        .event.SpotterEvent;

import javafx.scene.Node;

/**
 * One screen in the dashboard.
 *
 * The shell knows only this. It puts the title in
 * the navigation, puts the node in the content
 * area and refreshes the panel when it needs
 * refreshing, which means a new panel is one
 * class and one line in the shell rather than an
 * edit to a growing switch.
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
     * Called the first time the panel is opened,
     * and after that only when something it shows
     * has changed.
     */
    void refresh();

    /**
     * The changes that make this screen wrong.
     *
     * The Observer half of the arrangement. A
     * panel names the kinds of change it is
     * displaying, and the shell refreshes it when
     * one happens rather than on every visit.
     *
     * The default is nothing, which means the
     * opposite of what it looks like: a panel
     * that names no event is one the shell cannot
     * reason about, so it is refreshed on every
     * visit as before. Analytics is the case.
     * Its figures come from logged sets, which
     * arrive from another process, so there is
     * nothing in this window to announce them.
     */
    default Set<SpotterEvent> changedBy() {
        return Set.of();
    }
}
