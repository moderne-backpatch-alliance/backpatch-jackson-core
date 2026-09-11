package com.fasterxml.jackson.core.jsonptr;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamReadException;

public class JsonPointerOOME736Test extends BaseTest
{
    // such as https://github.com/nst/JSONTestSuite/blob/master/test_parsing/n_structure_100000_opening_arrays.json
    public void testDeepJsonPointer() throws Exception {
        int MAX_DEPTH = 120_000;
        // Create nesting of 120k arrays
        String INPUT = new String(new char[MAX_DEPTH]).replace("\0", "[");
        JsonParser parser = createParser(MODE_READER, INPUT);
        try {
            while (true) {
                parser.nextToken();
            }
        } catch (StreamReadException e) {
            // Adapted for the backpatched nesting-depth limit: the parse now stops at
            // 1000 rather than running on to end-of-input at 120k, so this hostile
            // input raises the depth error instead of "Unexpected end". What the test
            // is FOR is unchanged -- that pathAsPointer() can build the pointer for the
            // deepest context reached without OOME (jackson-core#736) -- and it is now
            // asserted at the deepest depth this artifact can be driven to.
            verifyException(e, "exceeds the maximum allowed nesting depth");
            JsonStreamContext parsingContext = parser.getParsingContext();
            JsonPointer jsonPointer = parsingContext.pathAsPointer(); // OOME
            String pointer = jsonPointer.toString();
            // 1000 array contexts are open when the 1001st is refused, and the root
            // context contributes no segment, so the pointer has 1000 segments.
            String expected = new String(new char[1000]).replace("\0", "/0");
            assertEquals(expected, pointer);
        }
        parser.close();
    }
}
