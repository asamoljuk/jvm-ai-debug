package com.antonsamoljuk.jvmaidbg.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingTest {

    @Test
    void knownModelUsesExactRates() {
        // gpt-4o-mini: $0.15/M input, $0.60/M output
        // 1000 input + 500 output = 0.00015 + 0.00030 = 0.00045
        double cost = Pricing.estimateUsd("gpt-4o-mini", 1000, 500);
        assertEquals(0.00045, cost, 1e-9);
        assertTrue(Pricing.isKnown("gpt-4o-mini"));
    }

    @Test
    void unknownModelUsesFallbackEstimate() {
        // Fallback: $1/M input, $5/M output
        double cost = Pricing.estimateUsd("unknown-future-model", 1000, 500);
        assertEquals(0.001 + 0.0025, cost, 1e-9);
        assertFalse(Pricing.isKnown("unknown-future-model"));
    }

    @Test
    void zeroTokensCostsNothing() {
        assertEquals(0.0, Pricing.estimateUsd("gpt-4o", 0, 0), 1e-12);
    }

    @Test
    void usagePlusAccumulates() {
        TokenUsage a = new TokenUsage("gpt-4o-mini", 100, 50, 0.001);
        TokenUsage b = new TokenUsage("gpt-4o-mini", 200, 75, 0.002);
        TokenUsage sum = a.plus(b);
        assertEquals("gpt-4o-mini", sum.model());
        assertEquals(300, sum.inputTokens());
        assertEquals(125, sum.outputTokens());
        assertEquals(0.003, sum.estimatedCostUsd(), 1e-9);
    }

    @Test
    void usagePlusDifferentModelsCombinesLabel() {
        TokenUsage a = new TokenUsage("gpt-4o-mini", 100, 50, 0.001);
        TokenUsage b = new TokenUsage("claude-sonnet-4-6", 200, 75, 0.005);
        TokenUsage sum = a.plus(b);
        assertEquals("(multiple)", sum.model());
        assertEquals(300, sum.inputTokens());
    }

    @Test
    void usageZeroIsAdditiveIdentity() {
        TokenUsage real = new TokenUsage("gpt-4o", 100, 50, 0.001);
        assertEquals(real.inputTokens(), TokenUsage.zero().plus(real).inputTokens());
    }
}
