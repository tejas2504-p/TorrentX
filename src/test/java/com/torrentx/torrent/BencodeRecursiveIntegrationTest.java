package com.torrentx.torrent;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BencodeRecursiveIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void testListContainingDictionaries() throws BencodeException {
        // ld3:foo4:spamed3:bar4:spamee -> List containing two dictionaries
        byte[] input = "ld3:foo4:spamed3:bar4:spamee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        List<Object> list = (List<Object>) decoder.decode();
        
        assertEquals(2, list.size());
        
        Map<String, Object> dict1 = (Map<String, Object>) list.get(0);
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) dict1.get("foo"));
        
        Map<String, Object> dict2 = (Map<String, Object>) list.get(1);
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) dict2.get("bar"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDictionaryContainingLists() throws BencodeException {
        // d4:listl4:spami42eee -> Dictionary containing a list
        byte[] input = "d4:listl4:spami42eee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        Map<String, Object> dict = (Map<String, Object>) decoder.decode();
        
        assertEquals(1, dict.size());
        List<Object> list = (List<Object>) dict.get("list");
        assertEquals(2, list.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) list.get(0));
        assertEquals(42L, list.get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDictionaryContainingDictionaries() throws BencodeException {
        // d3:bard3:fooi42eee -> Dictionary containing a dictionary
        byte[] input = "d3:bard3:fooi42eee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        Map<String, Object> dict = (Map<String, Object>) decoder.decode();
        
        assertEquals(1, dict.size());
        Map<String, Object> innerDict = (Map<String, Object>) dict.get("bar");
        assertEquals(42L, innerDict.get("foo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListsContainingLists() throws BencodeException {
        // ll4:spamei42ee -> List containing a list and an integer
        byte[] input = "ll4:spamei42ee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        List<Object> outerList = (List<Object>) decoder.decode();
        
        assertEquals(2, outerList.size());
        List<Object> innerList = (List<Object>) outerList.get(0);
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) innerList.get(0));
        assertEquals(42L, outerList.get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeeplyNestedStructures() throws BencodeException {
        // Create 10 layers of nested dictionaries: d1:ad1:ad1:ad1:ad1:ad1:ad1:ad1:ad1:ad1:ai42eeeeeeeeeee
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("d1:a");
        }
        sb.append("i42e");
        for (int i = 0; i < 10; i++) {
            sb.append("e");
        }
        
        byte[] input = sb.toString().getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        Object decoded = decoder.decode();
        
        Object current = decoded;
        for (int i = 0; i < 10; i++) {
            assertTrue(current instanceof Map);
            Map<String, Object> m = (Map<String, Object>) current;
            assertEquals(1, m.size());
            current = m.get("a");
        }
        assertEquals(42L, current);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEmptyNestedStructures() throws BencodeException {
        // ldee -> List containing empty dictionary
        byte[] input = "ldee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        List<Object> outerList = (List<Object>) decoder.decode();
        assertEquals(1, outerList.size());
        
        Map<String, Object> innerDict = (Map<String, Object>) outerList.get(0);
        assertTrue(innerDict.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMixedTypes() throws BencodeException {
        // d3:mixld3:fooi42eel4:spamei100eee -> Dictionary mapping "mix" to list of [ { "foo": 42 }, [ "spam" ], 100 ]
        byte[] input = "d3:mixld3:fooi42eel4:spamei100eee".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        Map<String, Object> map = (Map<String, Object>) decoder.decode();
        
        List<Object> list = (List<Object>) map.get("mix");
        assertEquals(3, list.size());
        
        Map<String, Object> innerDict = (Map<String, Object>) list.get(0);
        assertEquals(42L, innerDict.get("foo"));
        
        List<Object> innerList = (List<Object>) list.get(1);
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) innerList.get(0));
        
        assertEquals(100L, list.get(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPositionMaintenanceAfterNestedStructures() throws BencodeException {
        // d3:bard3:fooi42ee3:baz4:spame
        // Ensures that baz/spam is successfully parsed after nested dictionary "bar" is parsed.
        byte[] input = "d3:bard3:fooi42ee3:baz4:spame".getBytes(StandardCharsets.US_ASCII);
        BencodeDecoder decoder = new BencodeDecoder(input);
        Map<String, Object> map = (Map<String, Object>) decoder.decode();
        
        assertEquals(2, map.size());
        Map<String, Object> bar = (Map<String, Object>) map.get("bar");
        assertEquals(42L, bar.get("foo"));
        
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) map.get("baz"));
        
        // ld3:fooi42eei100ee -> List of [ { "foo": 42 }, 100 ]
        // Ensures that 100 is successfully parsed after nested dictionary.
        input = "ld3:fooi42eei100ee".getBytes(StandardCharsets.US_ASCII);
        decoder = new BencodeDecoder(input);
        List<Object> list = (List<Object>) decoder.decode();
        assertEquals(2, list.size());
        assertEquals(100L, list.get(1));
    }

    @Test
    void testMalformedAndTruncatedNestedInput() {
        // Truncated list in dictionary: d4:listl4:spame (missing outer dictionary terminator 'e')
        assertThrows(BencodeException.class, () -> 
            new BencodeDecoder("d4:listl4:spame".getBytes(StandardCharsets.US_ASCII)).decode()
        );

        // Truncated nested dictionary: d3:bard3:fooi42e (missing both inner and outer dict terminators)
        assertThrows(BencodeException.class, () -> 
            new BencodeDecoder("d3:bard3:fooi42e".getBytes(StandardCharsets.US_ASCII)).decode()
        );

        // Malformed nested key (non-string key in nested dict)
        assertThrows(BencodeException.class, () -> 
            new BencodeDecoder("d3:bard3:fooi42ei10ee".getBytes(StandardCharsets.US_ASCII)).decode()
        );

        // Truncated nested list: ll4:spame (missing outer list terminator)
        assertThrows(BencodeException.class, () -> 
            new BencodeDecoder("ll4:spame".getBytes(StandardCharsets.US_ASCII)).decode()
        );
    }
}