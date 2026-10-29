package ca.yorku.eecs3311.spotter.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes every exchange to a file as it happens.
 *
 * Two uses. A real reply captured once can be
 * replayed by ScriptedLLMClient forever after, so
 * a test can be written against something the
 * model actually said rather than something
 * invented. And when an agent misbehaves, the
 * recording shows exactly what it was asked and
 * what came back, which is the first thing anyone
 * wants when analysing a failure.
 *
 * A recording never contains the API key, because
 * the key is not part of a request.
 */
public final class RecordingLLMClient
        extends LLMClientDecorator {

    private final Path file;
    private final List<String> entries =
            new ArrayList<>();

    public RecordingLLMClient(
            LLMClient wrapped, Path file) {
        super(wrapped);
        if (file == null) {
            throw new IllegalArgumentException(
                    "A recording needs a file");
        }
        this.file = file;
    }

    @Override
    public LLMResponse complete(
            LLMRequest request) {
        LLMResponse response;
        try {
            response =
                wrapped().complete(request);
        } catch (LLMException e) {
            record(request, null,
                    e.getMessage());
            throw e;
        }
        record(request, response, null);
        return response;
    }

    private void record(LLMRequest request,
                        LLMResponse response,
                        String failure) {
        StringBuilder entry =
                new StringBuilder();
        entry.append("=== request (")
             .append(request.tier())
             .append(", ")
             .append(request.tools().size())
             .append(" tool(s)) ===\n")
             .append(request.userPrompt())
             .append('\n');

        if (failure != null) {
            entry.append("=== failed ===\n")
                 .append(failure)
                 .append('\n');
        } else {
            entry.append("=== reply (")
                 .append(response.usage()
                     .total())
                 .append(" tokens) ===\n")
                 .append(response.text())
                 .append('\n');
            for (ToolCall call
                    : response.toolCalls()) {
                entry.append("  wants tool: ")
                     .append(call.toolName())
                     .append(' ')
                     .append(call.arguments())
                     .append('\n');
            }
        }
        entries.add(entry.toString());
        flush();
    }

    private void flush() {
        try {
            Path parent = file.toAbsolutePath()
                    .getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file,
                String.join("\n", entries),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            // A recording is a convenience.
            // Losing it must not fail the task
            // the lifter actually asked for.
        }
    }

    public int recorded() {
        return entries.size();
    }
}
