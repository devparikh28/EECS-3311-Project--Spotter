package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        .ReportPanel;
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
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Spotter's dashboard.
 *
 * The shell and nothing else: a bar along the top
 * with the screens as tabs, one panel at a time
 * below it, and a context every panel shares. It
 * knows how to show a panel and knows nothing about
 * what any panel contains, so adding a screen is a
 * new class and one line here.
 *
 * Import and Profile are not tabs. They are things
 * done now and then, not places to live, so they
 * sit at the right of the bar beside the theme
 * icon, which keeps the tabs to the screens a
 * lifter opens every day.
 *
 * A panel is refreshed the first time it is
 * opened, and after that only when something it
 * shows has changed. The shell learns that from
 * the event bus rather than by guessing: a panel
 * names the kinds of change that concern it, the
 * panel on screen redraws at once, and the others
 * are marked stale and redrawn the next time they
 * are opened.
 */
public final class DashboardApp
        extends Application {

    /** Window width when it first opens. */
    public static final double WIDTH = 1240;

    /** Window height when it first opens. */
    public static final double HEIGHT = 760;

    /** Widest the content is allowed to get. */
    private static final double CONTENT_MAX = 1040;

    private GuiContext context;
    private final Map<Panel, Button> tabs =
            new LinkedHashMap<>();
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
    private final VBox drawer = new VBox(8);
    private Panel showing;

    @Override
    public void start(Stage stage) {
        context = new GuiContext(
                SpotterConfig.load(
                        envFile()));

        DashboardPanel dashboard =
                new DashboardPanel(context);
        ImportPanel importer =
                new ImportPanel(context);
        ProfilePanel profile =
                new ProfilePanel(context);

        List<Panel> everyday = List.of(
            dashboard,
            new TodayPanel(context),
            new PlanPanel(context),
            new AnalyticsPanel(context),
            new RecoveryPanel(context),
            new NutritionPanel(context),
            new CoachPanel(context),
            new ReportPanel(context));

        List<Panel> panels =
            new java.util.ArrayList<>(everyday);
        panels.add(importer);
        panels.add(profile);

        subscribe(panels);
        shell.setTop(topBar(everyday, importer,
                profile));
        shell.setCenter(withDrawer(
                contentArea()));

        Scene scene = new Scene(shell, WIDTH,
                HEIGHT);
        scene.getStylesheets().add(
            DashboardApp.class.getResource(
                "/gui/spotter.css")
                .toExternalForm());
        theme.apply(shell);

        stage.setMinWidth(1100);
        stage.setTitle("Spotter");
        stage.setScene(scene);
        stage.show();

        show(dashboard);
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

    private Region topBar(List<Panel> everyday,
                          Panel importer,
                          Panel profile) {
        Label mark = Cards.styled("S",
                "brand-mark");
        HBox brand = new HBox(10, mark,
            Cards.styled("Spotter",
                    "brand-name"));
        brand.setAlignment(Pos.CENTER_LEFT);

        HBox pills = new HBox(2);
        pills.getStyleClass().add("tabs");
        pills.setAlignment(Pos.CENTER);
        for (Panel panel : everyday) {
            Button button = new Button(
                    panel.title());
            button.getStyleClass()
                    .add("nav-button");
            button.setMinWidth(
                    Region.USE_PREF_SIZE);
            button.setOnAction(event ->
                    show(panel));
            tabs.put(panel, button);
            pills.getChildren().add(button);
        }

        Button theme = new Button();
        theme.getStyleClass().add(
                "theme-icon");
        javafx.scene.control.Tooltip.install(
            theme, new javafx.scene.control
                .Tooltip("Switch theme"));
        Runnable icon = () -> theme.setText(
            this.theme.isLight()
                ? "\u263E" : "\u2600");
        icon.run();
        theme.setOnAction(event -> {
            this.theme.toggle(shell);
            icon.run();
        });

        Button load = Cards.primary(
                new Button("Import"));
        load.getStyleClass().add("top-action");
        load.setMinWidth(Region.USE_PREF_SIZE);
        load.setOnAction(event ->
                show(importer));

        Button you = new Button("Profile");
        you.getStyleClass().add("top-ghost");
        you.setMinWidth(Region.USE_PREF_SIZE);
        you.setOnAction(event ->
                show(profile));
        tabs.put(importer, load);
        tabs.put(profile, you);

        Label coach = Cards.badge(
            context.hasModel()
                ? "COACH ON"
                : "NO KEY",
            context.hasModel()
                ? "good" : "quiet");

        coach.setMinWidth(Region.USE_PREF_SIZE);
        theme.setMinWidth(Region.USE_PREF_SIZE);
        HBox right = new HBox(8, coach, theme,
                you, load);
        right.setMinWidth(Region.USE_PREF_SIZE);
        pills.setMinWidth(Region.USE_PREF_SIZE);
        brand.setMinWidth(Region.USE_PREF_SIZE);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(16, brand,
            Cards.grow(), pills, Cards.grow(),
            right);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private Region contentArea() {
        VBox area = new VBox(0, panelTitle,
                panelDescription, content);
        area.getStyleClass().add("content");
        area.setMaxWidth(CONTENT_MAX);
        VBox.setVgrow(content,
                Priority.ALWAYS);

        HBox centred = new HBox(area);
        centred.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(area, Priority.ALWAYS);

        ScrollPane scroll =
                new ScrollPane(centred);
        scroll.setFitToWidth(true);
        scroll.getStyleClass()
                .add("content-scroll");
        return scroll;
    }

    /**
     * The quick coach: a chat that opens over
     * whatever screen is showing.
     *
     * A floating pill at the bottom left opens it,
     * so a question asked from the Today screen
     * does not mean leaving it. It is a second view
     * of the Coach screen's agent and memory, and
     * closes with its own button or the pill.
     */
    private Region withDrawer(Region content) {
        CoachPanel quick =
            new CoachPanel(context, true);
        quick.refresh();

        Button close = new Button("Close");
        close.setOnAction(event ->
                toggleDrawer());
        HBox bar = new HBox(8,
            Cards.styled("ASK YOUR COACH",
                "card-heading"),
            Cards.grow(), close);
        bar.setAlignment(Pos.CENTER_LEFT);

        drawer.getChildren().setAll(bar,
                quick.node());
        drawer.getStyleClass().add("drawer");
        drawer.setMaxWidth(400);
        drawer.setMaxHeight(Region
                .USE_PREF_SIZE);
        drawer.setVisible(false);
        drawer.setManaged(false);
        StackPane.setAlignment(drawer,
                Pos.BOTTOM_LEFT);
        StackPane.setMargin(drawer,
            new javafx.geometry.Insets(
                0, 0, 74, 22));

        Button ask = Cards.primary(
            new Button("\u2726  Ask coach"));
        ask.getStyleClass().add("ask-pill");
        ask.setOnAction(event ->
                toggleDrawer());
        StackPane.setAlignment(ask,
                Pos.BOTTOM_LEFT);
        StackPane.setMargin(ask,
            new javafx.geometry.Insets(
                0, 0, 22, 22));

        return new StackPane(content, drawer,
                ask);
    }

    private void toggleDrawer() {
        boolean open = !drawer.isVisible();
        drawer.setVisible(open);
        drawer.setManaged(open);
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
    private void show(Panel panel) {
        tabs.forEach((each, button) -> {
            button.getStyleClass().remove(
                    "nav-button-selected");
            if (each == panel) {
                button.getStyleClass().add(
                    "nav-button-selected");
            }
        });

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
