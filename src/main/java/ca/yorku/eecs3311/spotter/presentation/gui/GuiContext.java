package ca.yorku.eecs3311.spotter
        .presentation.gui;

import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .PlanValidator;
import ca.yorku.eecs3311.spotter.agent
        .ProfileValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .BlockRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .NutritionRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .ProfileRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteBlockRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteNutritionRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteProfileRepository;

/**
 * What every panel is given.
 *
 * The panels need the same handful of things, and
 * handing each one a database connection to make
 * its own repositories from would mean five
 * panels disagreeing about which profile is
 * current. So the context owns one of each and
 * every panel reads through it.
 *
 * It also owns the worker, because a panel
 * creating its own would start a thread per
 * screen, and the configuration, because whether
 * an API key exists changes what a panel offers
 * to do rather than only how it does it.
 */
public final class GuiContext
        implements AutoCloseable {

    private final SpotterConfig config;
    private final Database database;
    private final CoachController controller;
    private final ProfileRepository profiles;
    private final BlockRepository blocks;
    private final NutritionRepository
            nutrition;
    private final FxTaskRunner tasks;
    private final PromptBuilder prompts =
            new PromptBuilder();
    private final PlanValidator planValidator =
            new PlanValidator();
    private final ProfileValidator
            profileValidator =
                new ProfileValidator();

    public GuiContext(SpotterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException(
                "Configuration is required");
        }
        this.config = config;
        this.database = Database.atPath(
                config.databasePath());
        this.database.migrate();
        this.controller =
                new CoachController(database);
        this.profiles =
            new SqliteProfileRepository(
                    database);
        this.blocks = new SqliteBlockRepository(
                database, controller.catalog());
        this.nutrition =
            new SqliteNutritionRepository(
                database, controller.foods());
        this.tasks = new FxTaskRunner();
    }

    public SpotterConfig config() {
        return config;
    }

    public CoachController controller() {
        return controller;
    }

    public ProfileRepository profiles() {
        return profiles;
    }

    public BlockRepository blocks() {
        return blocks;
    }

    public NutritionRepository nutrition() {
        return nutrition;
    }

    public FxTaskRunner tasks() {
        return tasks;
    }

    public PromptBuilder prompts() {
        return prompts;
    }

    public PlanValidator planValidator() {
        return planValidator;
    }

    public ProfileValidator
            profileValidator() {
        return profileValidator;
    }

    /** A parser that can read plans and meals. */
    public ResponseParser parser() {
        return new ResponseParser(
            controller.catalog(),
            controller.foods());
    }

    /**
     * The lifter, if onboarding has been done.
     *
     * Empty is a normal state rather than an
     * error, and the panels say so instead of
     * inventing someone.
     */
    public Optional<LifterProfile> profile() {
        return profiles.current();
    }

    /**
     * Whether a model can be reached.
     *
     * Panels check this to decide what to offer.
     * A button that calls an absent model and
     * then apologises is worse than a button that
     * says what it needs.
     */
    public boolean hasModel() {
        return config.hasApiKey();
    }

    /**
     * A client for the agents, or empty when
     * there is no key. The caller decides what to
     * do about it, since a panel offering a
     * prepared demonstration and one simply
     * refusing are both reasonable.
     */
    public Optional<LLMClient> model() {
        if (!hasModel()) {
            return Optional.empty();
        }
        return Optional.of(
            new ca.yorku.eecs3311.spotter.agent
                .ClaudeClient(config));
    }

    @Override
    public void close() {
        tasks.close();
        controller.close();
    }
}
