package ca.yorku.eecs3311.spotter
        .presentation.support;

import ca.yorku.eecs3311.spotter.agent
        .AgentTrace;
import ca.yorku.eecs3311.spotter.agent
        .TokenUsage;

/**
 * What a run of an agent cost.
 *
 * Stage 2 asks for the token and money cost of
 * the AI tools used, and the only honest way to
 * report that is to measure it rather than
 * estimate it afterwards. The trace already
 * counts tokens; this turns the count into a line
 * worth reading.
 *
 * Rates are passed in rather than compiled in.
 * Published prices change, and a figure baked
 * into the source would go quietly stale and be
 * wrong in a report that claims to have measured
 * it.
 */
public final class UsageReport {

    /** Rate assumed when none is configured. */
    public static final double
            DEFAULT_INPUT_PER_MILLION = 1.0;

    /** Rate assumed when none is configured. */
    public static final double
            DEFAULT_OUTPUT_PER_MILLION = 5.0;

    private UsageReport() {
    }

    /**
     * One line describing a run.
     *
     * @param trace          the finished run
     * @param live           whether a real model
     *                       was called
     */
    public static String describe(
            AgentTrace trace, boolean live) {

        if (trace == null) {
            return "no run to report on";
        }
        if (!live) {
            return "prepared reply, so no tokens"
                + " were used and nothing was"
                + " spent";
        }

        TokenUsage used = trace.usage();
        if (used.total() == 0) {
            return trace.modelCalls()
                + " model call(s), token counts"
                + " not reported by the model";
        }

        return String.format(
            "%d model call(s), %d tokens in,"
            + " %d out, about %s at %.2f and"
            + " %.2f per million",
            trace.modelCalls(),
            used.inputTokens(),
            used.outputTokens(),
            money(used.costUsd(
                DEFAULT_INPUT_PER_MILLION,
                DEFAULT_OUTPUT_PER_MILLION)),
            DEFAULT_INPUT_PER_MILLION,
            DEFAULT_OUTPUT_PER_MILLION);
    }

    /**
     * Money, written so a fraction of a cent
     * reads as one rather than as zero.
     */
    private static String money(double usd) {
        if (usd < 0.01) {
            return "under a cent";
        }
        return String.format("%.3f US dollars",
                usd);
    }
}
