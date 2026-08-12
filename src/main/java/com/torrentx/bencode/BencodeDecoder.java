package com.torrentx.bencode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BencodeDecoder {
    private final byte[] data;
    private int index;
    private byte[] rawInfoBytes;

    public BencodeDecoder(byte[] data) {
        this.data = data;
        this.index = 0;
    }

    public byte[] getRawInfoBytes() {
        return rawInfoBytes;
    }

    public Object decode() throws BencodeException {
        if (index >= data.length) {
            throw new BencodeException("Unexpected end of data");
        }
        byte b = data[index];
        if (b == 'i') {
            return decodeInteger();
        } else if (b == 'l') {
            return decodeList();
        } else if (b == 'd') {
            return decodeDictionary();
        } else if (b >= '0' && b <= '9') {
            return decodeString();
        } else {
            throw new BencodeException("Invalid Bencode token at index " + index + ": " + (char) b);
        }
    }

    private Long decodeInteger() throws BencodeException {
        index++; // skip 'i'
        int start = index;
        while (index < data.length && data[index] != 'e') {
            index++;
        }
        if (index >= data.length) {
            throw new BencodeException("Integer missing closing 'e'");
        }
        String valStr = new String(data, start, index - start, StandardCharsets.US_ASCII);
        index++; // skip 'e'
        try {
            return Long.parseLong(valStr);
        } catch (NumberFormatException e) {
            throw new BencodeException("Invalid integer: " + valStr);
        }
    }

    private byte[] decodeString() throws BencodeException {
        int colonIndex = index;
        while (colonIndex < data.length && data[colonIndex] != ':') {
            colonIndex++;
        }
        if (colonIndex >= data.length) {
            throw new BencodeException("String missing colon separator");
        }
        String lenStr = new String(data, index, colonIndex - index, StandardCharsets.US_ASCII);
        int length;
        try {
            length = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
            throw new BencodeException("Invalid string length: " + lenStr);
        }
        index = colonIndex + 1; // skip colon
        if (index + length > data.length) {
            throw new BencodeException("String length " + length + " exceeds data boundary");
        }
        byte[] strData = new byte[length];
        System.arraycopy(data, index, strData, 0, length);
        index += length;
        return strData;
    }

    private List<Object> decodeList() throws BencodeException {
        index++; // skip 'l'
        List<Object> list = new ArrayList<>();
        while (index < data.length && data[index] != 'e') {
            list.add(decode());
        }
        if (index >= data.length) {
            throw new BencodeException("List missing closing 'e'");
        }
        index++; // skip 'e'
        return list;
    }

    private Map<String, Object> decodeDictionary() throws BencodeException {
        index++; // skip 'd'
        Map<String, Object> map = new LinkedHashMap<>();
        while (index < data.length && data[index] != 'e') {
            byte[] keyBytes = decodeString();
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            
            boolean isInfoKey = "info".equals(key);
            int infoStart = index;
            
            Object value = decode();
            
            if (isInfoKey) {
                int infoEnd = index;
                rawInfoBytes = new byte[infoEnd - infoStart];
                System.arraycopy(data, infoStart, rawInfoBytes, 0, rawInfoBytes.length);
            }
            
            map.put(key, value);
        }
        if (index >= data.length) {
            throw new BencodeException("Dictionary missing closing 'e'");
        }
        index++; // skip 'e'
        return map;
    }
}
