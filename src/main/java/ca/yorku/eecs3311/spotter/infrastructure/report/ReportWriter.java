package ca.yorku.eecs3311.spotter
        .infrastructure.report;

import java.io.IOException;
import java.nio.file.Path;

import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport;

/**
 * Turns a report into a file.
 *
 * One interface and a writer per format, so a
 * third format is a new class rather than an edit
 * to two existing ones.
 */
public interface ReportWriter {

    /** File extension without the dot. */
    String extension();

    void write(TrainingReport report, Path file)
            throws IOException;
}
