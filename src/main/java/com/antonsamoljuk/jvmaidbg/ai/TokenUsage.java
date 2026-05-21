package com.antonsamoljuk.jvmaidbg.ai;

/** Tokens consumed by one AI call, plus a USD estimate based on {@link Pricing}. */
public record TokenUsage(String model, int inputTokens, int outputTokens, double estimatedCostUsd) {

    public static TokenUsage zero() {
        return new TokenUsage("(none)", 0, 0, 0.0);
    }

    /** Accumulates two usage records. If the models differ, the model field becomes "(multiple)". */
    public TokenUsage plus(TokenUsage other) {
        if (other == null) return this;
        String combinedModel = this.model.equals(other.model) ? this.model : "(multiple)";
        return new TokenUsage(
                combinedModel,
                this.inputTokens + other.inputTokens,
                this.outputTokens + other.outputTokens,
                this.estimatedCostUsd + other.estimatedCostUsd);
    }

    public String formatShort() {
        return String.format("%d in / %d out (~$%.4f)", inputTokens, outputTokens, estimatedCostUsd);
    }
}
