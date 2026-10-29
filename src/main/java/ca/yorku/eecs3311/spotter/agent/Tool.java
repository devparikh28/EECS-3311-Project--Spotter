package ca.yorku.eecs3311.spotter.agent;

import java.util.Map;

/**
 * Something the agent may look up.
 *
 * Tools are how an agent learns anything about
 * this lifter. It is never told facts in the
 * prompt that it could fetch, and it never
 * invents them: a tool result is evidence, a
 * model's recollection is not.
 */
public interface Tool {

    /** How the model refers to it. */
    String name();

    /** What it is and when to use it. */
    ToolSchema schema();

    /**
     * Runs the tool.
     *
     * Returns a readable result the model can use.
     * A failure is returned as text rather than
     * thrown, because the model can often recover
     * from being told a lookup failed, and an
     * exception would end the whole task.
     */
    String execute(Map<String, String> arguments);
}
