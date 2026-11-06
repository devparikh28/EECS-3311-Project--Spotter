package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.util.prefs.Preferences;

import javafx.scene.Parent;

/**
 * Which of the two themes the lifter chose.
 *
 * The choice is remembered in the user's own
 * preferences store rather than in the Spotter
 * database, because it describes this window on
 * this machine and not the lifter's training.
 *
 * Switching is one style class on the root node.
 * The stylesheet defines every colour by name and
 * the light theme redefines those names, so no
 * panel knows which theme is showing and none
 * needs redrawing when it changes.
 */
public final class ThemePreference {

    /** The style class that selects the light theme. */
    public static final String LIGHT = "light";

    private static final String KEY = "theme";

    private final Preferences store =
        Preferences.userNodeForPackage(
                ThemePreference.class);

    private boolean light;

    public ThemePreference() {
        String forced = System.getProperty(
                "spotter.theme");
        if (forced != null) {
            light = "light".equalsIgnoreCase(
                    forced);
            return;
        }
        try {
            light = "light".equals(
                store.get(KEY, "dark"));
        } catch (RuntimeException e) {
            light = false;
        }
    }

    public boolean isLight() {
        return light;
    }

    /** Flips the theme, remembers it, and applies it. */
    public void toggle(Parent root) {
        light = !light;
        try {
            store.put(KEY,
                    light ? "light" : "dark");
        } catch (RuntimeException e) {
            // a theme that is not remembered
            // is still a theme
        }
        apply(root);
    }

    public void apply(Parent root) {
        root.getStyleClass().remove(LIGHT);
        if (light) {
            root.getStyleClass().add(LIGHT);
        }
    }
}
