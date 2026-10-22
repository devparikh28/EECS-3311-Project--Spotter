package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Reports what Spotter is configured to do and
 * whether its database is ready, creating the
 * database on first run.
 *
 * Useful on its own, and it is the first thing to
 * run when something is not working. It never
 * prints the API key, only whether one was found.
 */
@Command(
    name = "status",
    description =
        "Show configuration and database state.")
public final class StatusCommand
        implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--env"},
        paramLabel = "FILE",
        description =
            "Configuration file to read."
            + " Defaults to .env in the"
            + " current directory.")
    Path envFile = Path.of(".env");

    @Override
    public Integer call() {
        SpotterConfig config =
                SpotterConfig.load(envFile);

        var out = spec.commandLine().getOut();
        out.println("Spotter status");
        out.println();
        out.printf("  config file     %s%n",
                envFile);
        out.printf("  model           %s%n",
                config.model());
        out.printf("  reasoning model %s%n",
                config.reasoningModel());
        out.printf("  max tokens      %d%n",
                config.maxTokens());
        out.printf("  timeout         %d s%n",
                config.timeoutSeconds());
        out.printf("  API key         %s%n",
                config.hasApiKey()
                    ? "found"
                    : "not set, AI features"
                      + " are unavailable");

        Path dbPath = config.databasePath();
        out.printf("  database        %s%n",
                dbPath);

        try (Database database =
                     Database.atPath(dbPath)) {
            database.migrate();
            out.printf("  schema version  %d%n",
                    database.schemaVersion());
            out.printf("  tables          %d%n",
                    countTables(database));
        }

        if (!config.hasApiKey()) {
            out.println();
            out.println(
                "  Copy .env.example to .env"
                + " and set LLM_API_KEY to"
                + " enable the agent.");
        }
        out.flush();
        return 0;
    }

    private int countTables(Database database) {
        String sql = "SELECT COUNT(*)"
                + " FROM sqlite_master"
                + " WHERE type = 'table'";
        try (Statement s = database.connection()
                     .createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }
}
