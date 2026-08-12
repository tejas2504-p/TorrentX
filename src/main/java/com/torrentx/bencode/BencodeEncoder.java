package com.torrentx.bencode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BencodeEncoder {

    public static byte[] encode(Object o) throws BencodeException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            encode(o, baos);
        } catch (IOException e) {
            throw new BencodeException("Failed to encode object", e);
        }
        return baos.toByteArray();
    }

    private static void encode(Object o, ByteArrayOutputStream out) throws IOException, BencodeException {
        if (o == null) {
            throw new BencodeException("Cannot encode null object");
        }

        if (o instanceof Number) {
            out.write('i');
            out.write(o.toString().getBytes(StandardCharsets.US_ASCII));
            out.write('e');
        } else if (o instanceof String) {
            byte[] bytes = ((String) o).getBytes(StandardCharsets.UTF_8);
            out.write(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
            out.write(':');
            out.write(bytes);
        } else if (o instanceof byte[]) {
            byte[] bytes = (byte[]) o;
            out.write(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
            out.write(':');
            out.write(bytes);
        } else if (o instanceof List) {
            out.write('l');
            for (Object item : (List<?>) o) {
                encode(item, out);
            }
            out.write('e');
        } else if (o instanceof Map) {
            out.write('d');
            Map<?, ?> rawMap = (Map<?, ?>) o;
            TreeMap<String, Object> sortedMap = new TreeMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key;
                if (entry.getKey() instanceof byte[]) {
                    key = new String((byte[]) entry.getKey(), StandardCharsets.UTF_8);
                } else if (entry.getKey() instanceof String) {
                    key = (String) entry.getKey();
                } else {
                    throw new BencodeException("Dictionary keys must be strings or byte arrays");
                }
                sortedMap.put(key, entry.getValue());
            }

            for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
                byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                out.write(Integer.toString(keyBytes.length).getBytes(StandardCharsets.US_ASCII));
                out.write(':');
                out.write(keyBytes);
                encode(entry.getValue(), out);
            }
            out.write('e');
        } else {
            throw new BencodeException("Unsupported object type: " + o.getClass().getName());
        }
    }
}
