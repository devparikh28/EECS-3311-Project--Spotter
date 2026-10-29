package ca.yorku.eecs3311.spotter.agent;

/**
 * Assembles a prompt from its parts.
 *
 * This is the Builder from the Stage 1 design. It
 * exists so that prompt structure lives in one
 * place rather than being concatenated slightly
 * differently inside nine agents, and so the
 * retry wording is identical everywhere.
 */
public final class PromptBuilder {

    /**
     * Builds the prompt for one attempt.
     *
     * @param task      what to do
     * @param context   the facts gathered first
     * @param rejection why the previous reply was
     *                  rejected, or null on the
     *                  first attempt
     */
    public String build(String task,
                        String context,
                        String rejection) {
        StringBuilder prompt =
                new StringBuilder();

        if (context != null
                && !context.isBlank()) {
            prompt.append("Here is what is known"
                    + " about this lifter.\n\n")
                  .append(context.strip())
                  .append("\n\n");
        }

        prompt.append(task == null
                ? ""
                : task.strip());

        if (rejection != null
                && !rejection.isBlank()) {
            prompt.append("\n\nYour previous"
                + " answer was rejected for this"
                + " reason:\n")
                  .append(rejection.strip())
                  .append("\n\nCorrect it and"
                + " answer again. Do not repeat"
                + " the same mistake.");
        }
        return prompt.toString();
    }
}
