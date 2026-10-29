package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tools one agent may use.
 *
 * Each agent registers only what its task needs,
 * so a meal suggestion cannot reach training data
 * and a substitution cannot read the food
 * catalog. Narrowing what is on offer is cheaper
 * and more reliable than instructing a model not
 * to use something.
 */
public final class ToolManager {

    private final Map<String, Tool> tools =
            new LinkedHashMap<>();

    public ToolManager register(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException(
                    "A tool is required");
        }
        tools.put(tool.name(), tool);
        return this;
    }

    public List<ToolSchema> schemas() {
        List<ToolSchema> schemas =
                new ArrayList<>();
        for (Tool tool : tools.values()) {
            schemas.add(tool.schema());
        }
        return List.copyOf(schemas);
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public int size() {
        return tools.size();
    }

    /**
     * Runs a requested tool.
     *
     * An unknown tool, or one that throws, comes
     * back as text saying so. The model is told
     * what went wrong and given a chance to
     * choose differently, which is more useful
     * than failing the task outright.
     */
    public String execute(ToolCall call) {
        if (call == null) {
            return "No tool was requested.";
        }
        Tool tool = tools.get(call.toolName());
        if (tool == null) {
            return "There is no tool called \""
                + call.toolName()
                + "\". Available tools: "
                + String.join(", ",
                        tools.keySet()) + ".";
        }
        try {
            String result = tool.execute(
                    call.arguments());
            return result == null
                    || result.isBlank()
                ? "The tool returned nothing."
                : result;
        } catch (RuntimeException e) {
            return "The tool \"" + call.toolName()
                + "\" failed: "
                + e.getMessage();
        }
    }
}
