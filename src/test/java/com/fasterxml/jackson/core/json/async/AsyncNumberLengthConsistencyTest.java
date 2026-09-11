package com.fasterxml.jackson.core.json.async;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;

/**
 * Tests that the number-length limit is enforced on the async parser's INTEGER path when
 * digit-only input is fed across multiple chunks (no terminator, no {@code endOfInput()}).
 *<p>
 * Scope is upstream #1611's: the integer path only. The fraction and exponent paths are
 * deliberately NOT covered here, because this backpatch deliberately does not bound them --
 * see the manifest's notes for what that leaves open at this baseline.
 *<p>
 * Ported from upstream's {@code AsyncNumberLengthConsistencyTest} (#1611). Two
 * adaptations, neither of them a weakening: 2.14.x has no {@code StreamReadConstraints},
 * so the factory is the plain default one and the limit is the fixed internal
 * {@code MAX_NUMBER_LENGTH}; and the failure is a {@link JsonParseException} reported
 * through {@code _reportError} rather than a {@code StreamConstraintsException}.
 */
public class AsyncNumberLengthConsistencyTest
    extends com.fasterxml.jackson.core.BaseTest
{
    private static final int MAX_NUM_LEN = 1000;
    // Chunk size kept modest so the test runs quickly under CI but still exceeds
    // the number-length limit after the very first chunk.
    private static final int CHUNK_SIZE = 4 * 1024;
    // Hard cap on chunks fed: well past the limit but bounded so a regressed
    // build cannot OOM the CI machine.
    private static final int MAX_CHUNKS = 32;

    private final JsonFactory F = new JsonFactory();

    private ByteArrayFeeder _startValue(JsonParser ap) throws Exception {
        ByteArrayFeeder feeder = (ByteArrayFeeder) ap;
        byte[] preamble = utf8Bytes("{\"v\":");
        feeder.feedInput(preamble, 0, preamble.length);
        JsonToken t;
        while ((t = ap.nextToken()) != JsonToken.NOT_AVAILABLE) {
            if (t == null) {
                fail("Parser ended unexpectedly while draining preamble");
            }
        }
        return feeder;
    }

    private static byte[] _digits(int n) {
        byte[] digits = new byte[n];
        for (int i = 0; i < digits.length; i++) {
            digits[i] = (byte) ('1' + (i % 9));
        }
        return digits;
    }

    /**
     * Streams the integer portion of a number across many chunks. Asserts that the
     * limit is enforced promptly once the accumulated digit length exceeds it, rather
     * than only at value completion.
     */
    public void testIntegerPathStreamingChunksRejectsBeyondMaxNumberLength() throws Exception
    {
        JsonParser ap = F.createNonBlockingByteArrayParser();
        try {
            ByteArrayFeeder feeder = _startValue(ap);
            byte[] digits = _digits(CHUNK_SIZE);
            try {
                for (int c = 0; c < MAX_CHUNKS; c++) {
                    feeder.feedInput(digits, 0, digits.length);
                    JsonToken tt = ap.nextToken();
                    if (tt != JsonToken.NOT_AVAILABLE) {
                        fail("Expected NOT_AVAILABLE while streaming integer digits, got: " + tt);
                    }
                }
                fail("Async parser accepted " + (CHUNK_SIZE * MAX_CHUNKS)
                        + " integer digits with a limit of " + MAX_NUM_LEN
                        + "; expected JsonParseException");
            } catch (JsonParseException e) {
                verifyException(e, "Number value length");
            }
        } finally {
            ap.close();
        }
    }

    /**
     * Feeds chunks SMALLER than the limit, so the value can only exceed it by
     * accumulating across several streaming suspensions (not just a single oversized
     * first chunk). Asserts the exception fires at the expected boundary.
     */
    public void testIntegerPathSmallChunksAccumulateRejectAtBoundary() throws Exception
    {
        final int smallChunk = 100; // < MAX_NUM_LEN, so several chunks are needed
        // Limit is crossed once accumulated digits exceed MAX_NUM_LEN; with 100-digit
        // chunks that is the 11th chunk (1000 ok at chunk 10, 1100 > 1000 at chunk 11).
        final int expectedFailChunk = (MAX_NUM_LEN / smallChunk) + 1;

        JsonParser ap = F.createNonBlockingByteArrayParser();
        try {
            ByteArrayFeeder feeder = _startValue(ap);
            byte[] digits = _digits(smallChunk);
            int chunksFed = 0;
            try {
                for (int c = 0; c < MAX_CHUNKS; c++) {
                    feeder.feedInput(digits, 0, digits.length);
                    chunksFed++;
                    JsonToken tt = ap.nextToken();
                    if (tt != JsonToken.NOT_AVAILABLE) {
                        fail("Expected NOT_AVAILABLE while streaming integer digits, got: " + tt);
                    }
                }
                fail("Async parser accepted " + (smallChunk * MAX_CHUNKS)
                        + " integer digits with a limit of " + MAX_NUM_LEN
                        + "; expected JsonParseException");
            } catch (JsonParseException e) {
                verifyException(e, "Number value length");
                // Must accumulate across several chunks before firing...
                assertTrue("Exception fired on a single chunk; cross-chunk accumulation not exercised",
                        chunksFed > 1);
                // ...and fire as soon as the limit is passed, not arbitrarily later.
                assertTrue("JsonParseException raised too late: after " + chunksFed
                                + " chunks of " + smallChunk + " (expected ~" + expectedFailChunk + ")",
                        chunksFed <= expectedFailChunk + 1);
            }
        } finally {
            ap.close();
        }
    }

    /**
     * Guards against the validator becoming over-eager: an integer whose length is just
     * below the limit, fed across many small chunks, must still parse cleanly to the
     * expected value.
     */
    public void testIntegerPathJustUnderMaxNumberLengthParsesCleanly() throws Exception
    {
        final int digitCount = MAX_NUM_LEN - 1;
        StringBuilder sb = new StringBuilder(digitCount);
        for (int i = 0; i < digitCount; i++) {
            sb.append((char) ('1' + (i % 9)));
        }
        final String number = sb.toString();

        JsonParser ap = F.createNonBlockingByteArrayParser();
        try {
            ByteArrayFeeder feeder = _startValue(ap);
            byte[] digits = utf8Bytes(number);
            final int smallChunk = 100;
            for (int off = 0; off < digits.length; off += smallChunk) {
                int len = Math.min(smallChunk, digits.length - off);
                feeder.feedInput(digits, off, off + len);
                assertEquals("Expected NOT_AVAILABLE while streaming sub-limit integer digits",
                        JsonToken.NOT_AVAILABLE, ap.nextToken());
            }
            byte[] tail = utf8Bytes("}");
            feeder.feedInput(tail, 0, tail.length);
            feeder.endOfInput();

            assertEquals("Sub-limit integer should parse without JsonParseException",
                    JsonToken.VALUE_NUMBER_INT, ap.nextToken());
            assertEquals(number, ap.getText());
            assertEquals(JsonToken.END_OBJECT, ap.nextToken());
        } finally {
            ap.close();
        }
    }

    /**
     * Negative-number variant: a leading {@code '-'} routes through
     * {@code _startNegativeNumber()} and the {@code negMod == -1} branch of
     * {@code _finishNumberIntegralPart}, so the sign must be excluded from the
     * validated digit length.
     */
    public void testNegativeIntegerPathSmallChunksAccumulateRejectAtBoundary() throws Exception
    {
        final int smallChunk = 100;
        final int expectedFailChunk = (MAX_NUM_LEN / smallChunk) + 1;

        JsonParser ap = F.createNonBlockingByteArrayParser();
        try {
            ByteArrayFeeder feeder = (ByteArrayFeeder) ap;
            byte[] preamble = utf8Bytes("{\"v\":-");
            feeder.feedInput(preamble, 0, preamble.length);
            JsonToken t;
            while ((t = ap.nextToken()) != JsonToken.NOT_AVAILABLE) {
                if (t == null) {
                    fail("Parser ended unexpectedly while draining preamble");
                }
            }
            byte[] digits = _digits(smallChunk);
            int chunksFed = 0;
            try {
                for (int c = 0; c < MAX_CHUNKS; c++) {
                    feeder.feedInput(digits, 0, digits.length);
                    chunksFed++;
                    JsonToken tt = ap.nextToken();
                    if (tt != JsonToken.NOT_AVAILABLE) {
                        fail("Expected NOT_AVAILABLE while streaming integer digits, got: " + tt);
                    }
                }
                fail("Async parser accepted " + (smallChunk * MAX_CHUNKS)
                        + " negative integer digits with a limit of " + MAX_NUM_LEN
                        + "; expected JsonParseException");
            } catch (JsonParseException e) {
                verifyException(e, "Number value length");
                assertTrue("Exception fired on a single chunk; cross-chunk accumulation not exercised",
                        chunksFed > 1);
                assertTrue("JsonParseException raised too late: after " + chunksFed
                                + " chunks of " + smallChunk + " (expected ~" + expectedFailChunk + ")",
                        chunksFed <= expectedFailChunk + 1);
            }
        } finally {
            ap.close();
        }
    }
}
