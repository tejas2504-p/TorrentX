package com.torrentx.torrent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decoder for Bencode formatted data, which is used in torrent files and tracker protocols.
 */
public class BencodeDecoder {
    
    private final byte[] data;
    private int index = 0;
    private int depth = 0;
    
    private int infoStart = -1;
    private int infoEnd = -1;

    /**
     * Constructs a BencodeDecoder with raw byte data.
     *
     * @param data the Bencode formatted bytes.
     */
    public BencodeDecoder(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        this.data = data;
    }

    /**
     * Decodes the Bencode data into Java objects.
     *
     * @return the decoded object structure (Map, List, Long, or byte[]).
     * @throws BencodeException if data is malformed or has trailing characters.
     */
    public Object decode() throws BencodeException {
        this.index = 0;
        this.depth = 0;
        this.infoStart = -1;
        this.infoEnd = -1;
        
        if (data.length == 0) {
            throw new BencodeException("Empty data input");
        }
        
        Object result = parseValue();
        
        if (index < data.length) {
            throw new BencodeException("Trailing bytes found at index " + index);
        }
        
        return result;
    }

    /**
     * Retrieves the raw byte slice of the "info" dictionary parsed from the root dictionary.
     *
     * @return the raw bytes of the "info" dictionary, or null if not found.
     */
    public byte[] getRawInfoBytes() {
        if (infoStart == -1 || infoEnd == -1) {
            return null;
        }
        byte[] infoBytes = new byte[infoEnd - infoStart];
        System.arraycopy(data, infoStart, infoBytes, 0, infoBytes.length);
        return infoBytes;
    }

    private Object parseValue() throws BencodeException {
        byte b = peek();
        if (b == 'i') {
            return parseInteger();
        } else if (b == 'l') {
            return parseList();
        } else if (b == 'd') {
            return parseDictionary();
        } else if (b >= '0' && b <= '9') {
            return parseString();
        } else {
            throw new BencodeException("Unexpected character '" + (char) b + "' at index " + index);
        }
    }

    private Long parseInteger() throws BencodeException {
        expect((byte) 'i');
        
        int start = index;
        byte b = read();
        
        // Handle negative sign
        boolean isNegative = (b == '-');
        if (isNegative) {
            b = read();
            if (b == '0') {
                throw new BencodeException("Negative zero is not allowed at index " + (index - 1));
            }
        }
        
        // Handle leading zero
        if (b == '0') {
            if (peek() != 'e') {
                throw new BencodeException("Leading zeroes are not allowed in integer at index " + start);
            }
        }
        
        // Read digits until 'e'
        while (b != 'e') {
            if (b < '0' || b > '9') {
                throw new BencodeException("Invalid character '" + (char) b + "' in integer at index " + (index - 1));
            }
            b = read();
        }
        
        // Extract the integer string
        // index - 1 is the position of 'e'
        String numStr = new String(data, start, index - 1 - start, StandardCharsets.US_ASCII);
        try {
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            throw new BencodeException("Integer overflow or malformed number '" + numStr + "' at index " + start, e);
        }
    }

    private byte[] parseString() throws BencodeException {
        int start = index;
        byte b = read();
        
        // Read string length digits
        if (b == '0') {
            if (peek() != ':') {
                throw new BencodeException("Leading zeroes are not allowed in string length at index " + start);
            }
        }
        
        while (b != ':') {
            if (b < '0' || b > '9') {
                throw new BencodeException("Invalid character '" + (char) b + "' in string length at index " + (index - 1));
            }
            b = read();
        }
        
        // Parse the length
        String lenStr = new String(data, start, index - 1 - start, StandardCharsets.US_ASCII);
        int length;
        try {
            length = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
            throw new BencodeException("String length overflow '" + lenStr + "' at index " + start, e);
        }
        
        if (length < 0) {
            throw new BencodeException("Negative string length '" + length + "' at index " + start);
        }
        
        // Check bounds
        if (index + length > data.length) {
            throw new BencodeException("String length " + length + " exceeds remaining data at index " + index);
        }
        
        // Read the string content bytes
        byte[] stringBytes = new byte[length];
        System.arraycopy(data, index, stringBytes, 0, length);
        index += length;
        
        return stringBytes;
    }

    private List<Object> parseList() throws BencodeException {
        expect((byte) 'l');
        
        List<Object> list = new ArrayList<>();
        while (peek() != 'e') {
            list.add(parseValue());
        }
        
        expect((byte) 'e');
        return list;
    }

    private Map<String, Object> parseDictionary() throws BencodeException {
        expect((byte) 'd');
        depth++;
        
        Map<String, Object> map = new LinkedHashMap<>();
        byte[] prevKey = null;
        
        while (peek() != 'e') {
            int keyStart = index;
            // Keys must be strings
            byte nextByte = peek();
            if (nextByte < '0' || nextByte > '9') {
                throw new BencodeException("Dictionary key must be a string at index " + index);
            }
            
            byte[] keyBytes = parseString();
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            
            // Verify key order (lexicographically sorted by raw bytes)
            if (prevKey != null && compareByteArrays(prevKey, keyBytes) >= 0) {
                throw new BencodeException("Dictionary keys must be sorted lexicographically at index " + keyStart);
            }
            prevKey = keyBytes;
            
            // Parse the value
            boolean isInfoKey = (depth == 1 && "info".equals(key));
            if (isInfoKey) {
                infoStart = index;
            }
            
            Object value = parseValue();
            
            if (isInfoKey) {
                infoEnd = index;
            }
            
            map.put(key, value);
        }
        
        expect((byte) 'e');
        depth--;
        return map;
    }

    private byte peek() throws BencodeException {
        if (index >= data.length) {
            throw new BencodeException("Premature end of data at index " + index);
        }
        return data[index];
    }

    private byte read() throws BencodeException {
        if (index >= data.length) {
            throw new BencodeException("Premature end of data at index " + index);
        }
        return data[index++];
    }

    private void expect(byte expected) throws BencodeException {
        byte b = read();
        if (b != expected) {
            throw new BencodeException("Expected '" + (char) expected + "' but got '" + (char) b + "' at index " + (index - 1));
        }
    }

    private int compareByteArrays(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int diff = Byte.compareUnsigned(a[i], b[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return Integer.compare(a.length, b.length);
    }
}
