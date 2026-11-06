package ca.yorku.eecs3311.spotter
        .infrastructure.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport;
import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport.Section;

/**
 * The report as a spreadsheet.
 *
 * Every row starts with the section it belongs to
 * so the whole file is one table that a
 * spreadsheet can filter and a script can group,
 * rather than several tables stacked in one file
 * that nothing can read back.
 */
public final class CsvReportWriter
        implements ReportWriter {

    @Override
    public String extension() {
        return "csv";
    }

    @Override
    public void write(TrainingReport report,
                      Path file)
            throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("section,kind,field1,field2,"
            + "field3,field4,field5\n");

        for (Section section
                : report.sections()) {
            for (String line : section.lines()) {
                row(text, section.heading(),
                    "note", List.of(line));
            }
            if (section.hasTable()) {
                row(text, section.heading(),
                    "header", section.columns());
                for (List<String> cells
                        : section.rows()) {
                    row(text, section.heading(),
                        "row", cells);
                }
            }
        }
        Files.writeString(file, text.toString(),
                StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder out,
                            String section,
                            String kind,
                            List<String> cells) {
        out.append(quote(section)).append(',')
            .append(kind);
        for (String cell : cells) {
            out.append(',').append(quote(cell));
        }
        out.append('\n');
    }

    /** Quotes a value so a comma cannot split it. */
    static String quote(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",")
                || safe.contains("\"")
                || safe.contains("\n")) {
            return "\"" + safe.replace("\"",
                    "\"\"") + "\"";
        }
        return safe;
    }
}
