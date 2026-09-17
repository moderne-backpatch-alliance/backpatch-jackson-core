package com.fasterxml.jackson.core.constraints;

import com.fasterxml.jackson.core.async.ByteArrayFeeder;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.JsonFactory;


/**
 * Number Length Constraint Bypass in Non-Blocking (Async) JSON Parsers
 */
public class AsyncLargeNumberReadTest
    extends com.fasterxml.jackson.core.BaseTest
{
    private static final int TEST_NUMBER_LENGTH = StreamReadConstraints.DEFAULT_MAX_NUM_LEN * 2;

    private final JsonFactory JSON_F = newStreamFactory();

    public void testAsyncParserFailsTooLongInt() throws Exception {
        byte[] payload = buildPayloadWithLongInteger(TEST_NUMBER_LENGTH);

        try (JsonParser p = JSON_F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder byteArrayFeeder = (ByteArrayFeeder) p;
            byteArrayFeeder.feedInput(payload, 0, payload.length);
            byteArrayFeeder.endOfInput();
    
            _asyncParserFailsTooLongNumber(p, JsonToken.VALUE_NUMBER_INT);
        }
    }

    public void testAsyncParserFailsTooLongDecimal() throws Exception {
        byte[] payload = buildPayloadWithLongDecimal(TEST_NUMBER_LENGTH);

        try (JsonParser p = JSON_F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder byteArrayFeeder = (ByteArrayFeeder) p;
            byteArrayFeeder.feedInput(payload, 0, payload.length);
            byteArrayFeeder.endOfInput();

            _asyncParserFailsTooLongNumber(p, JsonToken.VALUE_NUMBER_FLOAT);
        }
    }

    public void testAsyncParserFailsTooLongDecimalWithExponent() throws Exception {
        byte[] payload = buildPayloadWithLongExponent(TEST_NUMBER_LENGTH);

        try (JsonParser p = JSON_F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder byteArrayFeeder = (ByteArrayFeeder) p;
            byteArrayFeeder.feedInput(payload, 0, payload.length);
            byteArrayFeeder.endOfInput();

            _asyncParserFailsTooLongNumber(p, JsonToken.VALUE_NUMBER_FLOAT);
        }
    }
    
    private void _asyncParserFailsTooLongNumber(JsonParser p, JsonToken tokenMatch) throws Exception {
        boolean foundNumber = false;
        try {
            while (p.nextToken() != null) {
                if (p.currentToken() == tokenMatch) {
                    foundNumber = true;
                    String numberText = p.getText();
                    assertEquals("Async parser silently accepted all " + TEST_NUMBER_LENGTH + " digits",
                            TEST_NUMBER_LENGTH, numberText.length());
                }
            }
            fail("Async parser must reject a " + TEST_NUMBER_LENGTH + "-digit number (number found? "+foundNumber+")");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Number length (");
            verifyException(e, "exceeds the maximum length");
        }
    }

    private byte[] buildPayloadWithLongInteger(int numDigits) {
        StringBuilder sb = new StringBuilder(numDigits + 10);
        sb.append("{\"v\":");
        for (int i = 0; i < numDigits; i++) {
            sb.append((char) ('1' + (i % 9)));
        }
        sb.append('}');
        return utf8Bytes(sb.toString());
    }

    private byte[] buildPayloadWithLongDecimal(int numDigits) {
        StringBuilder sb = new StringBuilder(numDigits + 10);
        sb.append("{\"v\":0.");
        for (int i = 0; i < numDigits; i++) {
            sb.append((char) ('1' + (i % 9)));
        }
        sb.append('}');
        return utf8Bytes(sb.toString());
    }

    private byte[] buildPayloadWithLongExponent(int numDigits) {
        StringBuilder sb = new StringBuilder(numDigits + 10);
        sb.append("{\"v\":1.1E");
        for (int i = 0; i < numDigits; i++) {
            sb.append((char) ('1' + (i % 9)));
        }
        return utf8Bytes(sb.toString());
    }
}
