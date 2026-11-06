package ca.yorku.eecs3311.spotter
        .application.report;

import java.time.LocalDate;
import java.util.List;

/**
 * A lifter's training on one page, as data.
 *
 * The report is built once and written many ways.
 * Keeping it as plain sections of lines and tables,
 * rather than as a PDF or a screen, is what lets
 * the window preview it, the PDF writer lay it out
 * and the CSV writer flatten it from the same
 * numbers, so the three can never disagree.
 *
 * @param title       the heading
 * @param generatedOn the day it describes
 * @param sections    its parts, in reading order
 */
public record TrainingReport(
        String title,
        LocalDate generatedOn,
        List<Section> sections) {

    public TrainingReport {
        sections = List.copyOf(sections);
    }

    /**
     * One part of a report: some sentences, and
     * optionally a table.
     *
     * @param heading the section's name
     * @param lines   sentences, shown above the table
     * @param columns the table's header, or empty
     * @param rows    the table's rows
     */
    public record Section(
            String heading,
            List<String> lines,
            List<String> columns,
            List<List<String>> rows) {

        public Section {
            lines = List.copyOf(lines);
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }

        public boolean hasTable() {
            return !columns.isEmpty()
                    && !rows.isEmpty();
        }
    }
}
