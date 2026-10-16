package com.fasterxml.jackson.core.io;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberInputLinearScanTest
    extends com.fasterxml.jackson.core.JUnit5TestBase
{
    // Inputs are sized so that a backtracking regexp check overruns the timeout by seconds.
    @Test
    void looksLikeValidNumberRejectsLongDigitRunQuickly()
    {
        final String digitsThenGarbage = _repeat('1', 50_000) + "x";
        final String digitsDotThenGarbage = _repeat('1', 25_000) + "." + _repeat('1', 25_000) + "x";

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertFalse(NumberInput.looksLikeValidNumber(digitsThenGarbage));
            assertFalse(NumberInput.looksLikeValidNumber(digitsDotThenGarbage));
        });
    }

    @Test
    void looksLikeValidNumberAcceptsLongDigitRunQuickly()
    {
        final String digits = _repeat('1', 50_000);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertTrue(NumberInput.looksLikeValidNumber(digits));
            assertTrue(NumberInput.looksLikeValidNumber(digits + "." + digits + "e10"));
        });
    }

    private static String _repeat(char c, int count)
    {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
