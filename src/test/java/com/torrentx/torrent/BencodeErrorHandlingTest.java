package com.torrentx.torrent;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BencodeErrorHandlingTest {

    @Test
    void testEmptyInput() {
        // Empty input should fail predictably with a BencodeException
        BencodeException ex = assertThrows(BencodeException.class, () -> 
            new BencodeDecoder(new byte[0]).decode()
        );
        assertEquals("Empty data input", ex.getMessage());
    }

    @Test
    void testUnexpectedEndOfInput() {
        // Truncated integer
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i42".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Truncated string
        assertThrows(BencodeException.class, () -> new BencodeDecoder("4:sp".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Truncated list
        assertThrows(BencodeException.class, () -> new BencodeDecoder("l4:spam".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Truncated dictionary
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:foo4:spam".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testInvalidTypeMarker() {
        // Invalid type markers should throw BencodeException
        assertThrows(BencodeException.class, () -> new BencodeDecoder("x".getBytes(StandardCharsets.US_ASCII)).decode());
        assertThrows(BencodeException.class, () -> new BencodeDecoder("lx".getBytes(StandardCharsets.US_ASCII)).decode());
        assertThrows(BencodeException.class, () -> new BencodeDecoder("dx".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testInvalidStringLength() {
        // Non-digit string length prefix
        assertThrows(BencodeException.class, () -> new BencodeDecoder("abc:spam".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Negative sign string length prefix (which is treated as invalid character marker)
        assertThrows(BencodeException.class, () -> new BencodeDecoder("-5:spam".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // String length overflow
        assertThrows(BencodeException.class, () -> new BencodeDecoder("9999999999:spam".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Leading zero in string length
        assertThrows(BencodeException.class, () -> new BencodeDecoder("03:abc".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testMissingColon() {
        // Missing colon separator
        assertThrows(BencodeException.class, () -> new BencodeDecoder("4spam".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testTruncatedString() {
        // String content length mismatch
        assertThrows(BencodeException.class, () -> new BencodeDecoder("4:spa".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testInvalidInteger() {
        // Negative zero is not allowed
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i-0e".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Leading zeroes are not allowed
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i03e".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Double negative signs
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i--3e".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Non-numeric characters in integer
        assertThrows(BencodeException.class, () -> new BencodeDecoder("iabc3e".getBytes(StandardCharsets.US_ASCII)).decode());
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i-abc3e".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Empty integer value
        assertThrows(BencodeException.class, () -> new BencodeDecoder("ie".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testMissingIntegerTerminator() {
        // Missing integer terminator 'e'
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i42".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testInvalidList() {
        // Invalid type marker inside list
        assertThrows(BencodeException.class, () -> new BencodeDecoder("l4:spami42ex".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testMissingListTerminator() {
        // Missing list terminator 'e'
        assertThrows(BencodeException.class, () -> new BencodeDecoder("l4:spami42e".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testInvalidDictionary() {
        // Missing value for dictionary key
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:foo".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Integer as dictionary key (keys must be strings)
        assertThrows(BencodeException.class, () -> new BencodeDecoder("di42e4:spame".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Missing value for second key in dictionary
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:foo4:spam3:bar".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testMissingDictionaryTerminator() {
        // Missing dictionary terminator 'e'
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:bar4:spam".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testInvalidDictionaryKey() {
        // Duplicate key
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:foo4:spam3:fooi42ee".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Out-of-order key
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:foo4:spam3:bar4:spame".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testInvalidNesting() {
        // Missing outer list terminator
        assertThrows(BencodeException.class, () -> new BencodeDecoder("ld3:fooi42ee".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Missing outer dictionary terminator
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:barld3:fooi42eee".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testIntegerOverflow() {
        // Long.MAX_VALUE + 1
        BencodeException ex1 = assertThrows(BencodeException.class, () -> 
            new BencodeDecoder("i9223372036854775808e".getBytes(StandardCharsets.US_ASCII)).decode()
        );
        assertTrue(ex1.getMessage().contains("Integer overflow"));

        // Long.MIN_VALUE - 1
        BencodeException ex2 = assertThrows(BencodeException.class, () -> 
            new BencodeDecoder("i-9223372036854775809e".getBytes(StandardCharsets.US_ASCII)).decode()
        );
        assertTrue(ex2.getMessage().contains("Integer overflow"));

        // Massive overflow
        BencodeException ex3 = assertThrows(BencodeException.class, () -> 
            new BencodeDecoder("i9999999999999999999999999999999e".getBytes(StandardCharsets.US_ASCII)).decode()
        );
        assertTrue(ex3.getMessage().contains("Integer overflow"));
    }
}