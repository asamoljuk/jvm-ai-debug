package com.antonsamoljuk.jvmaidbg.ai;

import java.util.Map;

/**
 * USD pricing per 1 million tokens for known models. Update as providers change rates.
 * Unknown models fall back to a conservative estimate so users still see a number.
 */
public final class Pricing {

    // [inputPricePerMillion, outputPricePerMillion] in USD
    private static final Map<String, double[]> RATES = Map.ofEntries(
            // OpenAI
            Map.entry("gpt-4o-mini",        new double[]{0.15,  0.60}),
            Map.entry("gpt-4o",             new double[]{2.50, 10.00}),
            Map.entry("gpt-4-turbo",        new double[]{10.00, 30.00}),
            Map.entry("gpt-4",              new double[]{30.00, 60.00}),
            Map.entry("gpt-3.5-turbo",      new double[]{0.50,  1.50}),
            // Anthropic
            Map.entry("claude-sonnet-4-6",  new double[]{3.00, 15.00}),
            Map.entry("claude-sonnet-4-5",  new double[]{3.00, 15.00}),
            Map.entry("claude-opus-4-7",    new double[]{15.00, 75.00}),
            Map.entry("claude-opus-4",      new double[]{15.00, 75.00}),
            Map.entry("claude-haiku-4-5",   new double[]{1.00,  5.00}),
            Map.entry("claude-haiku-3-5",   new double[]{0.80,  4.00})
    );

    private Pricing() {}

    /** Returns USD estimate for the given (model, tokens). Falls back to a generic rate if unknown. */
    public static double estimateUsd(String model, int inputTokens, int outputTokens) {
        double[] rates = RATES.get(model);
        if (rates == null) {
            // Conservative fallback: $1/M input, $5/M output — a middle-of-road rate.
            return (inputTokens * 1.0 + outputTokens * 5.0) / 1_000_000.0;
        }
        return (inputTokens * rates[0] + outputTokens * rates[1]) / 1_000_000.0;
    }

    /** True if we have an exact rate for this model (not a fallback estimate). */
    public static boolean isKnown(String model) {
        return RATES.containsKey(model);
    }
}
