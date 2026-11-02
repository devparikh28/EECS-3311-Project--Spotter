package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .AnalyticsPanel;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels.Cards;
import ca.yorku.eecs3311.spotter
        .presentation.gui.panels
        .DashboardPanel;
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
 * Opening a panel refreshes it rather than
 * trusting what it drew last time, because an
 * import on one screen changes the numbers on
 * another and a dashboard showing stale figures
 * is worse than one that pauses.
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
            new ProfilePanel(context));

        shell.setLeft(navigation(panels));
        shell.setCenter(contentArea());

        Scene scene = new Scene(shell, WIDTH,
                HEIGHT);
        scene.getStylesheets().add(
            DashboardApp.class.getResource(
                "/gui/spotter.css")
                .toExternalForm());

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
        panel.refresh();
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
