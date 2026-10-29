package ca.yorku.eecs3311.spotter.agent;

/**
 * Which model a task deserves.
 *
 * Cost and latency are design concerns here. Most
 * of what Spotter asks for is small and bounded,
 * such as choosing a substitute from a list or
 * writing one sentence of rationale, and does not
 * need the stronger model. Generating a whole
 * training block does.
 */
public enum ModelTier {

    /** Cheap and quick. The default. */
    FAST,

    /** Stronger and dearer, for open ended work. */
    REASONING
}
