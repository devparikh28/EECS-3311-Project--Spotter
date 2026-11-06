package ca.yorku.eecs3311.spotter
        .infrastructure.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport;
import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport.Section;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common
        .PDRectangle;
import org.apache.pdfbox.pdmodel.font
        .PDType1Font;
import org.apache.pdfbox.pdmodel.font
        .Standard14Fonts;

/**
 * The report as a PDF a lifter can hand to a coach.
 *
 * Laid out by hand on A4 with the standard
 * Helvetica that every PDF reader has, so nothing
 * is embedded and nothing can fail to load. The
 * trade is that only Latin text is drawn: anything
 * Helvetica cannot encode is replaced with a
 * question mark rather than aborting the export.
 */
public final class PdfReportWriter
        implements ReportWriter {

    private static final float MARGIN = 54;
    private static final float BODY = 10.5f;
    private static final float LEADING = 15;

    private final PDType1Font regular =
        new PDType1Font(Standard14Fonts
            .FontName.HELVETICA);
    private final PDType1Font bold =
        new PDType1Font(Standard14Fonts
            .FontName.HELVETICA_BOLD);

    @Override
    public String extension() {
        return "pdf";
    }

    @Override
    public void write(TrainingReport report,
                      Path file)
            throws IOException {
        try (PDDocument document =
                new PDDocument()) {
            Page page = new Page(document);

            page.line(report.title(), bold, 20,
                    28);
            page.line("Generated "
                + report.generatedOn(),
                regular, 10, 22);

            for (Section section
                    : report.sections()) {
                page.need(60);
                page.line(section.heading(),
                    bold, 13, 20);
                for (String line
                        : section.lines()) {
                    for (String wrapped
                            : wrap(line,
                                regular, BODY,
                                page.width())) {
                        page.line(wrapped,
                            regular, BODY,
                            LEADING);
                    }
                }
                if (section.hasTable()) {
                    table(page, section);
                }
                page.gap(10);
            }
            page.close();
            document.save(file.toFile());
        }
    }

    private void table(Page page,
                       Section section)
            throws IOException {
        int columns = section.columns().size();
        float cell = page.width() / columns;

        page.cells(section.columns(), cell,
                bold);
        for (List<String> row : section.rows()) {
            page.cells(row, cell, regular);
        }
    }

    private static List<String> wrap(
            String text, PDType1Font font,
            float size, float width)
            throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current =
                new StringBuilder();
        for (String word
                : clean(text).split(" ")) {
            String trial = current.length() == 0
                ? word
                : current + " " + word;
            if (font.getStringWidth(trial)
                    / 1000 * size > width
                    && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(trial);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** Text Helvetica can draw. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace('–', '-')
            .replace('—', '-')
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replaceAll("[^\\x20-\\x7E]", "?");
    }

    /** A page being filled, and the next one. */
    private final class Page {

        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        Page(PDDocument document)
                throws IOException {
            this.document = document;
            open();
        }

        float width() {
            return PDRectangle.A4.getWidth()
                    - 2 * MARGIN;
        }

        private void open() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage next =
                new PDPage(PDRectangle.A4);
            document.addPage(next);
            stream = new PDPageContentStream(
                    document, next);
            y = PDRectangle.A4.getHeight()
                    - MARGIN;
        }

        /** Starts a new page if too little is left. */
        void need(float room)
                throws IOException {
            if (y - room < MARGIN) {
                open();
            }
        }

        void gap(float amount) {
            y -= amount;
        }

        void line(String text, PDType1Font font,
                  float size, float advance)
                throws IOException {
            need(advance);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(clean(text));
            stream.endText();
            y -= advance;
        }

        void cells(List<String> values,
                   float cell, PDType1Font font)
                throws IOException {
            need(LEADING);
            float x = MARGIN;
            for (String value : values) {
                stream.beginText();
                stream.setFont(font, BODY);
                stream.newLineAtOffset(x, y);
                stream.showText(fit(
                    clean(value), font, cell));
                stream.endText();
                x += cell;
            }
            y -= LEADING;
        }

        /** Trims a value to the width of its column. */
        private String fit(String value,
                           PDType1Font font,
                           float width)
                throws IOException {
            String shown = value;
            while (shown.length() > 1
                    && font.getStringWidth(shown)
                        / 1000 * BODY
                        > width - 6) {
                shown = shown.substring(0,
                    shown.length() - 1);
            }
            return shown;
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
