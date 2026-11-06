package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .AnalyticsPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels.Cards;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels.CoachPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .DashboardPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .ImportPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .NutritionPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels.PlanPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .ProfilePanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels.TodayPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .RecoveryPanel;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Spotter's dashboard.
 *
 * The shell and nothing else: a navigation column
 * on the left, one panel at a time on the right,
 * and a context every panel shares. It knows how
 * to show a panel and knows nothing about what
 * any panel contains, so adding a screen is a new
 * class and one line here.
 *
 * A panel is refreshed the first time it is
 * opened, and after that only when something it
 * shows has changed. The shell learns that from
 * the event bus rather than by guessing: a panel
 * names the kinds of change that concern it, the
 * panel on screen redraws at once, and the others
 * are marked stale and redrawn the next time they
 * are opened. Refreshing on every visit also
 * worked, and queried the database for figures
 * that nothing had touched.
 */
public final class DashboardApp
        extends Application {

    /** Window width when it first opens. */
    public static final double WIDTH = 1040;

    /** Window height when it first opens. */
    public static final double HEIGHT = 720;

    private GuiContext context;
    private final List<Button> navButtons =
            new ArrayList<>();
    private final BorderPane shell =
            new BorderPane();
    private final VBox content = new VBox(
            Cards.GAP);
    private final Label panelTitle =
            Cards.styled("", "panel-title");
    private final Label panelDescription =
            Cards.styled("",
                    "panel-description");
    private final Set<Panel> stale =
            new HashSet<>();
    private final Set<Panel> drawn =
            new HashSet<>();
    private final ThemePreference theme =
            new ThemePreference();
    private Panel showing;

    @Override
    public void start(Stage stage) {
        context = new GuiContext(
                SpotterConfig.load(
                        envFile()));

        List<Panel> panels = List.of(
            new DashboardPanel(context),
            new TodayPanel(context),
            new PlanPanel(context),
            new AnalyticsPanel(context),
            new RecoveryPanel(context),
            new NutritionPanel(context),
            new CoachPanel(context),
            new ImportPanel(context),
            new ProfilePanel(context));

        subscribe(panels);
        shell.setLeft(navigation(panels));
        shell.setCenter(contentArea());

        Scene scene = new Scene(shell, WIDTH,
                HEIGHT);
        scene.getStylesheets().add(
            DashboardApp.class.getResource(
                "/gui/spotter.css")
                .toExternalForm());

        theme.apply(shell);

        stage.setTitle("Spotter");
        stage.setScene(scene);
        stage.show();

        show(panels.get(0), 0);
    }

    /**
     * Where the database and key come from.
     *
     * The same .env the command line reads, so a
     * lifter who imported from a terminal sees
     * their own data here rather than an empty
     * dashboard pointed at a different file.
     */
    private static Path envFile() {
        return Path.of(".env");
    }

    private Region navigation(
            List<Panel> panels) {
        VBox nav = new VBox();
        nav.getStyleClass().add("nav");

        nav.getChildren().addAll(
            Cards.styled("Spotter", "nav-title"),
            Cards.styled("strength coach",
                "nav-subtitle"));

        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            int index = i;
            Button button = new Button(
                    panel.title());
            button.getStyleClass()
                    .add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event ->
                    show(panel, index));
            navButtons.add(button);
            nav.getChildren().add(button);
        }

        Region filler = new Region();
        VBox.setVgrow(filler,
                Priority.ALWAYS);
        nav.getChildren().add(filler);
        Button toggle = new Button();
        toggle.getStyleClass().add(
                "theme-toggle");
        toggle.setMaxWidth(Double.MAX_VALUE);
        Runnable label = () -> toggle.setText(
            theme.isLight()
                ? "Switch to dark theme"
                : "Switch to light theme");
        label.run();
        toggle.setOnAction(event -> {
            theme.toggle(shell);
            label.run();
        });
        nav.getChildren().add(toggle);
        nav.getChildren().add(
            Cards.styled(context.hasModel()
                    ? "coach: connected"
                    : "coach: no API key",
                "nav-footer"));
        return nav;
    }

    private Region contentArea() {
        VBox area = new VBox(0, panelTitle,
                panelDescription, content);
        area.getStyleClass().add("content");
        VBox.setVgrow(content,
                Priority.ALWAYS);

        ScrollPane scroll =
                new ScrollPane(area);
        scroll.setFitToWidth(true);
        scroll.getStyleClass()
                .add("content-scroll");
        return scroll;
    }

    /**
     * Tells the bus which panels care about what.
     *
     * The shell does the subscribing so that a
     * panel is not responsible for remembering to
     * register itself, which is the usual way an
     * event bus ends up with one screen that
     * quietly never updates.
     */
    private void subscribe(List<Panel> panels) {
        for (Panel panel : panels) {
            context.events().subscribeAll(
                panel.changedBy(),
                event -> changed(panel));
        }
    }

    /**
     * Something a panel shows has changed.
     *
     * The panel on screen is redrawn now because
     * the lifter is looking at it. The rest are
     * marked and redrawn when they are next
     * opened, which is the first moment anyone
     * could notice.
     */
    private void changed(Panel panel) {
        if (panel == showing) {
            panel.refresh();
            return;
        }
        stale.add(panel);
    }

    /** Puts one panel on screen. */
    private void show(Panel panel, int index) {
        for (int i = 0; i < navButtons.size();
                i++) {
            Button button = navButtons.get(i);
            button.getStyleClass().remove(
                    "nav-button-selected");
            if (i == index) {
                button.getStyleClass().add(
                    "nav-button-selected");
            }
        }

        panelTitle.setText(panel.title());
        panelDescription.setText(
                panel.description());

        content.getChildren().clear();
        content.getChildren().add(panel.node());
        showing = panel;

        boolean first = drawn.add(panel);
        boolean unwatched =
                panel.changedBy().isEmpty();
        if (first || unwatched
                || stale.remove(panel)) {
            panel.refresh();
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * For running without Maven. The usual way in
     * is mvn javafx:run, which supplies the
     * modules JavaFX needs on the path.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
