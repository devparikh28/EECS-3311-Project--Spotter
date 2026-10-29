package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shape every agent task follows.
 *
 * This is the Template Method from the Stage 1
 * design, and it is the reason nine agents are
 * feasible rather than nine separate problems.
 * run() fixes the order of the work and the
 * safety rules; a subclass supplies only what
 * makes its task different.
 *
 * The order is: gather the context the task
 * needs, build a prompt, call the model, run any
 * tools it asks for and go back to it, parse the
 * reply into a domain object, then validate that
 * object. Nothing reaches the rest of the system
 * until validation passes.
 *
 * A rejected reply is sent back once with the
 * reason attached, because a model told precisely
 * what was wrong usually fixes it. A second
 * rejection returns a failure rather than a bad
 * plan. That is the principle the whole design
 * rests on: the model proposes, deterministic
 * code disposes.
 *
 * run() is final on purpose. A subclass that
 * could override it could skip validation, and
 * then the guarantee would be a convention rather
 * than a guarantee.
 *
 * @param <T> what this agent produces
 */
public abstract class CoachAgent<T> {

    /** How many times tools may be run. */
    public static final int MAX_TOOL_ROUNDS = 4;

    /** Retries allowed after a rejected reply. */
    public static final int MAX_RETRIES = 1;

    private final LLMClient model;
    private final PromptBuilder prompts;

    protected CoachAgent(LLMClient model,
                         PromptBuilder prompts) {
        if (model == null || prompts == null) {
            throw new IllegalArgumentException(
                "An agent needs a model and a"
                + " prompt builder");
        }
        this.model = model;
        this.prompts = prompts;
    }

    /** A name for traces and messages. */
    public abstract String name();

    /** Which model this task deserves. */
    protected ModelTier tier() {
        return ModelTier.FAST;
    }

    /** The rules and role for this task. */
    protected abstract String systemPrompt();

    /**
     * The facts this task needs, gathered before
     * the model is asked anything.
     */
    protected abstract String gatherContext();

    /** The task itself, in words. */
    protected abstract String describeTask();

    /**
     * The tools this agent may use. Returning an
     * empty manager is fine for a task that needs
     * no lookups.
     */
    protected abstract ToolManager createToolset();

    /**
     * Turns a reply into a domain object, or
     * empty when it cannot be read at all.
     */
    protected abstract Optional<T> parse(
            String reply);

    /**
     * Checks a parsed object against the rules.
     *
     * @return empty when acceptable, otherwise
     *         the reason it is not, which is sent
     *         back to the model on the retry
     */
    protected abstract Optional<String> validate(
            T parsed);

    /** Runs the task. */
    public final AgentResult<T> run() {
        AgentTrace trace = new AgentTrace();
        ToolManager tools = createToolset();
        trace.step("agent " + name()
                + " started with "
                + tools.size() + " tool(s)");

        String context;
        try {
            context = gatherContext();
            trace.step("gathered context");
        } catch (RuntimeException e) {
            return AgentResult.failure(
                "Could not gather the"
                + " information this needs: "
                + e.getMessage(), trace);
        }

        String rejection = null;

        for (int attempt = 0;
                attempt <= MAX_RETRIES;
                attempt++) {

            String prompt = prompts.build(
                    describeTask(), context,
                    rejection);

            String reply;
            try {
                reply = converse(prompt, tools,
                        trace);
            } catch (LLMException e) {
                return AgentResult.failure(
                    "The coach is unavailable: "
                    + e.getMessage(), trace);
            }

            Optional<T> parsed = parse(reply);
            if (parsed.isEmpty()) {
                rejection = "The reply could not"
                    + " be read. Answer with"
                    + " only the JSON described,"
                    + " and nothing else.";
                trace.validationFailed(
                        "unreadable reply");
                continue;
            }

            Optional<String> problem =
                    validate(parsed.get());
            if (problem.isEmpty()) {
                trace.step("validation passed");
                return AgentResult.success(
                        parsed.get(), trace);
            }
            rejection = problem.get();
            trace.validationFailed(rejection);
        }

        return AgentResult.failure(
            "The coach could not produce"
            + " something acceptable. Last"
            + " problem: " + rejection, trace);
    }

    /**
     * One exchange with the model, running any
     * tools it asks for and returning with their
     * results until it answers or the rounds run
     * out.
     */
    private String converse(
            String prompt,
            ToolManager tools,
            AgentTrace trace) {

        StringBuilder conversation =
                new StringBuilder(prompt);

        for (int round = 0;
                round <= MAX_TOOL_ROUNDS;
                round++) {

            LLMRequest request = new LLMRequest(
                systemPrompt(),
                conversation.toString(),
                tools.schemas(), tier(),
                maxTokens());

            LLMResponse response =
                    model.complete(request);
            trace.modelCalled(response.usage());

            if (!response.wantsTools()) {
                return response.text();
            }

            if (round == MAX_TOOL_ROUNDS) {
                trace.step("tool rounds"
                    + " exhausted; asking for an"
                    + " answer with what is"
                    + " known");
                conversation.append(
                    "\n\nNo more lookups are"
                    + " available. Answer with"
                    + " what you have.");
                continue;
            }

            List<String> results =
                    new ArrayList<>();
            for (ToolCall call
                    : response.toolCalls()) {
                String result =
                        tools.execute(call);
                trace.toolCalled(call.toolName(),
                        result);
                results.add(call.toolName()
                        + ": " + result);
            }
            conversation.append(
                "\n\nResults of the lookups you"
                + " asked for:\n")
                .append(String.join("\n",
                        results));
        }
        return "";
    }

    protected int maxTokens() {
        return 4096;
    }

    protected LLMClient model() {
        return model;
    }
}
