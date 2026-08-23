package com.torrentx.torrent;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BencodeDecoderTest {

    @Test
    void testConstructorWithNullData() {
        assertThrows(IllegalArgumentException.class, () -> new BencodeDecoder(null));
    }

    @Test
    void testEmptyInputThrowsException() {
        BencodeDecoder decoder = new BencodeDecoder(new byte[0]);
        assertThrows(BencodeException.class, decoder::decode);
    }

    @Test
    void testDecodeIntegers() throws BencodeException {
        // Positive integer
        BencodeDecoder decoder = new BencodeDecoder("i42e".getBytes(StandardCharsets.US_ASCII));
        assertEquals(42L, decoder.decode());

        // Negative integer
        decoder = new BencodeDecoder("i-42e".getBytes(StandardCharsets.US_ASCII));
        assertEquals(-42L, decoder.decode());

        // Zero
        decoder = new BencodeDecoder("i0e".getBytes(StandardCharsets.US_ASCII));
        assertEquals(0L, decoder.decode());

        // Large integer
        decoder = new BencodeDecoder("i9223372036854775807e".getBytes(StandardCharsets.US_ASCII));
        assertEquals(Long.MAX_VALUE, decoder.decode());
    }

    @Test
    void testDecodeInvalidIntegers() {
        // Negative zero
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i-0e".getBytes(StandardCharsets.US_ASCII)).decode());

        // Leading zeroes
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i03e".getBytes(StandardCharsets.US_ASCII)).decode());
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i-03e".getBytes(StandardCharsets.US_ASCII)).decode());

        // Unclosed
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i42".getBytes(StandardCharsets.US_ASCII)).decode());

        // Non-numeric
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i42ae".getBytes(StandardCharsets.US_ASCII)).decode());

        // Overflow
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i9223372036854775808e".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testDecodeStrings() throws BencodeException {
        // Normal string
        BencodeDecoder decoder = new BencodeDecoder("4:spam".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) decoder.decode());

        // Empty string
        decoder = new BencodeDecoder("0:".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(new byte[0], (byte[]) decoder.decode());

        // Unicode encoded bytes
        String unicodeStr = "hello 世界";
        byte[] utf8Bytes = unicodeStr.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = (utf8Bytes.length + ":").getBytes(StandardCharsets.US_ASCII);
        byte[] bencodedUnicode = new byte[prefix.length + utf8Bytes.length];
        System.arraycopy(prefix, 0, bencodedUnicode, 0, prefix.length);
        System.arraycopy(utf8Bytes, 0, bencodedUnicode, prefix.length, utf8Bytes.length);
        decoder = new BencodeDecoder(bencodedUnicode);
        assertArrayEquals(utf8Bytes, (byte[]) decoder.decode());

        // Arbitrary binary bytes
        byte[] binaryData = new byte[]{1, 2, 3, 0, 4, 5, -1, -128};
        byte[] encodedData = new byte[binaryData.length + 2];
        encodedData[0] = '8';
        encodedData[1] = ':';
        System.arraycopy(binaryData, 0, encodedData, 2, binaryData.length);
        decoder = new BencodeDecoder(encodedData);
        assertArrayEquals(binaryData, (byte[]) decoder.decode());
    }

    @Test
    void testDecodeInvalidStrings() {
        // Leading zero in length
        assertThrows(BencodeException.class, () -> new BencodeDecoder("03:abc".getBytes(StandardCharsets.US_ASCII)).decode());

        // Premature EOF / Truncated input
        assertThrows(BencodeException.class, () -> new BencodeDecoder("4:spa".getBytes(StandardCharsets.US_ASCII)).decode());

        // Invalid length character
        assertThrows(BencodeException.class, () -> new BencodeDecoder("4a:spam".getBytes(StandardCharsets.US_ASCII)).decode());

        // Missing ':' separator
        assertThrows(BencodeException.class, () -> new BencodeDecoder("4spam".getBytes(StandardCharsets.US_ASCII)).decode());

        // Negative length (invalid length format/character)
        assertThrows(BencodeException.class, () -> new BencodeDecoder("-4:spam".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    void testDecodeLists() throws BencodeException {
        // Empty list
        BencodeDecoder decoder = new BencodeDecoder("le".getBytes(StandardCharsets.US_ASCII));
        List<?> emptyList = (List<?>) decoder.decode();
        assertTrue(emptyList.isEmpty());

        // Mixed list
        decoder = new BencodeDecoder("l4:spami42ee".getBytes(StandardCharsets.US_ASCII));
        List<?> list = (List<?>) decoder.decode();
        assertEquals(2, list.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) list.get(0));
        assertEquals(42L, list.get(1));

        // Nested lists
        decoder = new BencodeDecoder("ll4:spamee".getBytes(StandardCharsets.US_ASCII));
        List<?> nestedList = (List<?>) decoder.decode();
        assertEquals(1, nestedList.size());
        List<?> innerList = (List<?>) nestedList.get(0);
        assertEquals(1, innerList.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) innerList.get(0));

        // Dictionaries inside lists
        decoder = new BencodeDecoder("ld3:bar4:spamee".getBytes(StandardCharsets.US_ASCII));
        List<?> dictsInList = (List<?>) decoder.decode();
        assertEquals(1, dictsInList.size());
        Map<?, ?> innerDict = (Map<?, ?>) dictsInList.get(0);
        assertEquals(1, innerDict.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) innerDict.get("bar"));

        // Lists inside dictionaries
        decoder = new BencodeDecoder("d4:listl4:spami42eee".getBytes(StandardCharsets.US_ASCII));
        Map<?, ?> listInDict = (Map<?, ?>) decoder.decode();
        assertEquals(1, listInDict.size());
        List<?> innerList2 = (List<?>) listInDict.get("list");
        assertEquals(2, innerList2.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) innerList2.get(0));
        assertEquals(42L, innerList2.get(1));
    }

    @Test
    void testDecodeInvalidLists() {
        // Unclosed list (missing terminating 'e')
        assertThrows(BencodeException.class, () -> new BencodeDecoder("l4:spami42e".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Truncated list input (just 'l')
        assertThrows(BencodeException.class, () -> new BencodeDecoder("l".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDecodeDictionaries() throws BencodeException {
        // Empty dictionary
        BencodeDecoder decoder = new BencodeDecoder("de".getBytes(StandardCharsets.US_ASCII));
        Map<String, Object> emptyMap = (Map<String, Object>) decoder.decode();
        assertTrue(emptyMap.isEmpty());

        // Sorted dictionary
        decoder = new BencodeDecoder("d3:bar4:spam3:fooi42ee".getBytes(StandardCharsets.US_ASCII));
        Map<String, Object> map = (Map<String, Object>) decoder.decode();
        assertEquals(2, map.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) map.get("bar"));
        assertEquals(42L, map.get("foo"));
    }

    @Test
    void testDecodeInvalidDictionaries() {
        // Keys not sorted lexicographically
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:fooi42e3:bar4:spame".getBytes(StandardCharsets.US_ASCII)).decode());

        // Non-string key
        assertThrows(BencodeException.class, () -> new BencodeDecoder("di42ei10ee".getBytes(StandardCharsets.US_ASCII)).decode());

        // Unclosed dictionary
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:bar4:spam".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRawInfoExtraction() throws BencodeException {
        // Simple case: info is dictionary
        byte[] input = "d8:announce35:http://tracker.example.com/announce4:infod3:fooi42eee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        
        Map<String, Object> decoded = (Map<String, Object>) decoder.decode();
        assertNotNull(decoded);
        
        byte[] rawInfo = decoder.getRawInfoBytes();
        assertNotNull(rawInfo);
        assertEquals("d3:fooi42ee", new String(rawInfo, StandardCharsets.US_ASCII));

        // When info key doesn't exist
        byte[] inputNoInfo = "d8:announce35:http://tracker.example.com/announcee".getBytes(StandardCharsets.US_ASCII);
        decoder = new BencodeDecoder(inputNoInfo);
        decoder.decode();
        assertNull(decoder.getRawInfoBytes());
    }

    @Test
    void testNestedInfoIsolation() throws BencodeException {
        // When "info" key is nested inside another list or map and not at the root
        byte[] input = "d4:dictd4:infod3:fooi42eeee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        decoder.decode();
        
        // Root depth is 0, dictionary has depth 1, the inner dictionary has depth 2.
        // Therefore, "info" is at depth 2 and should not be extracted as the root info block.
        assertNull(decoder.getRawInfoBytes());
    }

    @Test
    void testTrailingBytes() {
        assertThrows(BencodeException.class, () -> new BencodeDecoder("i42eextra".getBytes(StandardCharsets.US_ASCII)).decode());
        assertThrows(BencodeException.class, () -> new BencodeDecoder("0: ".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDictionaryEdgeCases() throws BencodeException {
        // Raw key bytes preservation: construct a key with invalid UTF-8 (e.g. byte 0x80)
        byte[] rawKey = new byte[]{(byte) 0x80};
        byte[] rawValue = "spam".getBytes(StandardCharsets.US_ASCII);
        
        // Bencoded: d1:<0x80>4:spame
        byte[] input = new byte[1 + 2 + 1 + 2 + 4 + 1];
        input[0] = 'd';
        input[1] = '1';
        input[2] = ':';
        input[3] = (byte) 0x80;
        input[4] = '4';
        input[5] = ':';
        System.arraycopy(rawValue, 0, input, 6, 4);
        input[10] = 'e';
        
        BencodeDecoder decoder = new BencodeDecoder(input);
        Map<String, Object> decoded = (Map<String, Object>) decoder.decode();
        assertEquals(1, decoded.size());
        
        // Get the key in string format and extract its bytes using ISO-8859-1
        String keyStr = decoded.keySet().iterator().next();
        byte[] extractedKeyBytes = keyStr.getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(rawKey, extractedKeyBytes);
        assertArrayEquals(rawValue, (byte[]) decoded.values().iterator().next());
    }

    @Test
    void testDecodeDictionaryDuplicateKeys() {
        // Duplicate keys: d3:foo4:spam3:fooi42ee
        byte[] input = "d3:foo4:spam3:fooi42ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(BencodeException.class, () -> new BencodeDecoder(input).decode());
    }

    @Test
    void testDecodeDictionaryTruncatedAndMissingTerminator() {
        // Missing terminator
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:foo4:spam".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Truncated keys
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:fo".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Truncated value
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d3:foo4:sp".getBytes(StandardCharsets.US_ASCII)).decode());
        
        // Just 'd'
        assertThrows(BencodeException.class, () -> new BencodeDecoder("d".getBytes(StandardCharsets.US_ASCII)).decode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNestedDictionariesAndLists() throws BencodeException {
        // Nested dictionaries and lists
        byte[] input = "d3:bard3:fooi42ee4:listl4:spamdeee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        Map<String, Object> map = (Map<String, Object>) decoder.decode();
        
        assertEquals(2, map.size());
        
        Map<String, Object> bar = (Map<String, Object>) map.get("bar");
        assertEquals(42L, bar.get("foo"));
        
        List<Object> list = (List<Object>) map.get("list");
        assertEquals(2, list.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) list.get(0));
        assertTrue(((Map<String, Object>) list.get(1)).isEmpty());
    }
}
