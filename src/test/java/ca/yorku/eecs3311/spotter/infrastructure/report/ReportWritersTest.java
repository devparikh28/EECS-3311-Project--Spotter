package ca.yorku.eecs3311.spotter
        .infrastructure.report;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport;
import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport.Section;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWritersTest {

    @TempDir
    Path folder;

    private static TrainingReport sample() {
        return new TrainingReport("Test report",
            LocalDate.of(2026, 11, 6),
            List.of(
              new Section("Strength",
                List.of("Estimated, with a comma."),
                List.of("Lift", "Current"),
                List.of(List.of("Squat",
                    "147.1 kg"))),
              new Section("Recovery",
                List.of("Nothing here – yet"
                    + " é."),
                List.of(), List.of())));
    }

    @Test
    void csvHasOneRowPerLineAndQuotesCommas()
            throws IOException {
        Path file = folder.resolve("r.csv");
        new CsvReportWriter()
                .write(sample(), file);

        List<String> lines = Files.readAllLines(
            file, StandardCharsets.UTF_8);
        assertThat(lines.get(0))
            .startsWith("section,kind");
        assertThat(lines).contains(
            "Strength,note,\"Estimated, with a"
            + " comma.\"",
            "Strength,header,Lift,Current",
            "Strength,row,Squat,147.1 kg");
    }

    @Test
    void pdfIsAValidDocumentContainingTheText()
            throws IOException {
        Path file = folder.resolve("r.pdf");
        new PdfReportWriter()
                .write(sample(), file);

        try (PDDocument document =
                Loader.loadPDF(file.toFile())) {
            assertThat(document.getNumberOfPages())
                .isEqualTo(1);
            String text = new PDFTextStripper()
                .getText(document);
            assertThat(text)
                .contains("Test report")
                .contains("Squat")
                .contains("147.1 kg");
        }
    }

    @Test
    void textHelveticaCannotDrawDoesNotAbortTheExport()
            throws IOException {
        Path file = folder.resolve("odd.pdf");
        new PdfReportWriter()
                .write(sample(), file);

        assertThat(Files.size(file))
                .isGreaterThan(500);
        assertThat(PdfReportWriter.clean(
                "a – b é 中"))
            .isEqualTo("a - b ? ?");
    }

    @Test
    void aLongReportRunsOntoMorePages()
            throws IOException {
        List<List<String>> rows =
                new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            rows.add(List.of("2026-10-" + i,
                    "x"));
        }
        TrainingReport big = new TrainingReport(
            "Big", LocalDate.of(2026, 11, 6),
            List.of(new Section("Rows",
                List.of(),
                List.of("Date", "Value"), rows)));

        Path file = folder.resolve("big.pdf");
        new PdfReportWriter().write(big, file);

        try (PDDocument document =
                Loader.loadPDF(file.toFile())) {
            assertThat(document.getNumberOfPages())
                .isGreaterThan(1);
        }
    }
}
