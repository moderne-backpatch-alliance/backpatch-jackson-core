package com.fasterxml.jackson.core.read;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class ParserErrorHandlingTest
    extends com.fasterxml.jackson.core.BaseTest
{
    public void testInvalidKeywordsBytes() throws Exception {
        _testInvalidKeywords(MODE_INPUT_STREAM);
        _testInvalidKeywords(MODE_INPUT_STREAM_THROTTLED);
        _testInvalidKeywords(MODE_DATA_INPUT);
    }

    public void testInvalidKeywordsChars() throws Exception {
        _testInvalidKeywords(MODE_READER);
    }

    // Tests for [core#105] ("eager number parsing misses errors")
    public void testMangledIntsBytes() throws Exception {
        _testMangledNumbersInt(MODE_INPUT_STREAM);
        _testMangledNumbersInt(MODE_INPUT_STREAM_THROTTLED);

        // 02-Jun-2017, tatu: Fails to fail; should check whether this is expected
        //   (since DataInput can't do look-ahead)
//        _testMangledNumbersInt(MODE_DATA_INPUT);
    }

    public void testMangledFloatsBytes() throws Exception {
        _testMangledNumbersFloat(MODE_INPUT_STREAM);
        _testMangledNumbersFloat(MODE_INPUT_STREAM_THROTTLED);

        // 02-Jun-2017, tatu: Fails as expected, unlike int one. Bit puzzling...
        _testMangledNumbersFloat(MODE_DATA_INPUT);
    }

    public void testMangledNumbersChars() throws Exception {
        _testMangledNumbersInt(MODE_READER);
        _testMangledNumbersFloat(MODE_READER);
    }

    // [core#1698]: every parser backend must honor `maxErrorTokenLength`;
    //   `UTF8DataInputJsonParser` used to accumulate the whole token instead
    public void testErrorTokenLengthBoundedInAllModes()
            throws Exception
    {
        final int maxLen = 256;
        final JsonFactory f = newStreamFactory();
        // Broken token far longer than the limit: must be truncated, not accumulated in full
        final String doc = _buildBrokenJsonOfLength(50 * maxLen);
        // limit, plus appended "..."
        final String expToken = _brokenToken(maxLen) + "...";

        for (int mode : ALL_MODES) {
            try (JsonParser p = createParser(f, mode, doc)) {
                p.nextToken();
                p.nextToken();
                fail("Should not pass, mode: "+mode);
            } catch (JsonProcessingException e) {
                assertEquals("mode: "+mode, expToken, _unrecognizedToken(e.getMessage()));
            }
        }
    }

    // Short broken token (below limit) must be reported verbatim, without
    // duplicated leading char, in all modes
    public void testShortErrorTokenReportedExactlyInAllModes()
            throws Exception
    {
        final JsonFactory f = newStreamFactory();
        final String doc = "{\"key\":abc!}";

        for (int mode : ALL_MODES) {
            try (JsonParser p = createParser(f, mode, doc)) {
                p.nextToken();
                p.nextToken();
                fail("Should not pass, mode: "+mode);
            } catch (JsonProcessingException e) {
                assertEquals("mode: "+mode, "abc", _unrecognizedToken(e.getMessage()));
            }
        }
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _testInvalidKeywords(int mode) throws Exception
    {
        doTestInvalidKeyword1(mode, "nul");
        doTestInvalidKeyword1(mode, "Null");
        doTestInvalidKeyword1(mode, "nulla");
        doTestInvalidKeyword1(mode, "fal");
        doTestInvalidKeyword1(mode, "False");
        doTestInvalidKeyword1(mode, "fals0");
        doTestInvalidKeyword1(mode, "falsett0");
        doTestInvalidKeyword1(mode, "tr");
        doTestInvalidKeyword1(mode, "truE");
        doTestInvalidKeyword1(mode, "treu");
        doTestInvalidKeyword1(mode, "trueenough");
        doTestInvalidKeyword1(mode, "C");

        // Multi-byte (non-ASCII) identifier chars right after keyword
        doTestInvalidKeyword1(mode, "trueé");
        doTestInvalidKeyword1(mode, "nullé");
        doTestInvalidKeyword1(mode, "false中x");

        // Multi-byte (non-ASCII) identifier char at start of token
        doTestInvalidKeyword1(mode, "éabc");
        doTestInvalidKeyword1(mode, "中x");

        // Multi-byte char that is NOT part of token (NBSP): after keyword, or at value start
        _testNonTokenChar(mode, "[true\u00A0]");
        _testNonTokenChar(mode, "{\"a\":null\u00A0}");
        _testNonTokenChar(mode, "[\u00A0]");
        _testNonTokenChar(mode, "{\"a\":\u00A0}");
    }

    private void doTestInvalidKeyword1(int mode, String value)
        throws IOException
    {
        String doc = "{ \"key1\" : "+value+" }";
        JsonParser p = createParser(mode, doc);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        // Note that depending on parser impl, we may
        // get the exception early or late...
        try {
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            p.nextToken();
            fail("Expected an exception for malformed value keyword");
        } catch (JsonParseException jex) {
            verifyException(jex, "Unrecognized token");
            verifyException(jex, value);
        } finally {
            p.close();
        }

        // Try as root-level value as well:
        doc = value + " "; // may need space after for DataInput
        p = createParser(mode, doc);
        try {
            p.nextToken();
            fail("Expected an exception for malformed value keyword");
        } catch (JsonParseException jex) {
            verifyException(jex, "Unrecognized token");
            verifyException(jex, value);
        } finally {
            p.close();
        }
    }

    // Must be reported as an error: not skipped, nor mis-decoded
    private void _testNonTokenChar(int mode, String doc) throws IOException
    {
        try (JsonParser p = createParser(mode, doc)) {
            while (p.nextToken() != null) { }
            fail("Expected an exception for invalid non-token char; doc: "+doc);
        } catch (JsonParseException jex) {
            verifyException(jex, "Unexpected character");
        }
    }

    // Extracts X from "Unrecognized token 'X': was expecting ..."
    private String _unrecognizedToken(String msg)
    {
        final String prefix = "Unrecognized token '";
        final int start = msg.indexOf(prefix);
        assertTrue("message: "+msg, start >= 0);
        final int end = msg.indexOf("': was expecting", start);
        assertTrue("message: "+msg, end > start);
        return msg.substring(start + prefix.length(), end);
    }

    private String _buildBrokenJsonOfLength(int len)
    {
        return "{\"key\":" + _brokenToken(len) + "!}";
    }

    // Varied (not repeating single) chars so that duplicated/dropped chars are detectable
    private String _brokenToken(int len)
    {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }

    private void _testMangledNumbersInt(int mode) throws Exception
    {
        JsonParser p = createParser(mode, "123true");
        try {
            JsonToken t = p.nextToken();
            fail("Should have gotten an exception; instead got token: "+t);
        } catch (JsonParseException e) {
            verifyException(e, "expected space");
        }
        p.close();
    }

    private void _testMangledNumbersFloat(int mode) throws Exception
    {
        // Also test with floats
        JsonParser p = createParser(mode, "1.5false");
        try {
            JsonToken t = p.nextToken();
            fail("Should have gotten an exception; instead got token: "+t);
        } catch (JsonParseException e) {
            verifyException(e, "expected space");
        }
        p.close();
    }
}
