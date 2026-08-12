package com.torrentx.bencode;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class BencodeParserTest {

    @Test
    public void testDecodeInteger() throws BencodeException {
        BencodeDecoder decoder = new BencodeDecoder("i42e".getBytes(StandardCharsets.US_ASCII));
        assertEquals(42L, decoder.decode());

        decoder = new BencodeDecoder("i-100e".getBytes(StandardCharsets.US_ASCII));
        assertEquals(-100L, decoder.decode());

        decoder = new BencodeDecoder("i0e".getBytes(StandardCharsets.US_ASCII));
        assertEquals(0L, decoder.decode());
    }

    @Test
    public void testDecodeString() throws BencodeException {
        BencodeDecoder decoder = new BencodeDecoder("4:spam".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) decoder.decode());

        decoder = new BencodeDecoder("0:".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(new byte[0], (byte[]) decoder.decode());
    }

    @Test
    public void testDecodeList() throws BencodeException {
        BencodeDecoder decoder = new BencodeDecoder("l4:spami42ee".getBytes(StandardCharsets.US_ASCII));
        List<?> list = (List<?>) decoder.decode();
        assertEquals(2, list.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) list.get(0));
        assertEquals(42L, list.get(1));
    }

    @Test
    public void testDecodeDictionary() throws BencodeException {
        BencodeDecoder decoder = new BencodeDecoder("d3:cow3:moo4:spam4:eggse".getBytes(StandardCharsets.US_ASCII));
        Map<?, ?> map = (Map<?, ?>) decoder.decode();
        assertEquals(2, map.size());
        assertArrayEquals("moo".getBytes(StandardCharsets.US_ASCII), (byte[]) map.get("cow"));
        assertArrayEquals("eggs".getBytes(StandardCharsets.US_ASCII), (byte[]) map.get("spam"));
    }

    @Test
    public void testRoundtripEncoding() throws BencodeException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cow", "moo");
        map.put("spam", 42L);
        List<Object> list = new ArrayList<>();
        list.add("egg");
        list.add(100L);
        map.put("list", list);

        byte[] encoded = BencodeEncoder.encode(map);
        BencodeDecoder decoder = new BencodeDecoder(encoded);
        Map<?, ?> decodedMap = (Map<?, ?>) decoder.decode();

        assertEquals(3, decodedMap.size());
        assertArrayEquals("moo".getBytes(StandardCharsets.UTF_8), (byte[]) decodedMap.get("cow"));
        assertEquals(42L, decodedMap.get("spam"));
        
        List<?> decodedList = (List<?>) decodedMap.get("list");
        assertEquals(2, decodedList.size());
        assertArrayEquals("egg".getBytes(StandardCharsets.UTF_8), (byte[]) decodedList.get(0));
        assertEquals(100L, decodedList.get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMetainfoParsingSingleFile() throws BencodeException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "testfile.txt");
        info.put("piece length", 262144L);
        byte[] mockPieces = new byte[40];
        Arrays.fill(mockPieces, (byte) 1);
        info.put("pieces", mockPieces);
        info.put("length", 500000L);

        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "http://tracker.example.com/announce");
        torrent.put("info", info);

        byte[] rawInfoBytes = BencodeEncoder.encode(info);
        byte[] rawTorrentBytes = BencodeEncoder.encode(torrent);

        BencodeDecoder decoder = new BencodeDecoder(rawTorrentBytes);
        Map<String, Object> decodedTorrent = (Map<String, Object>) decoder.decode();
        byte[] extractedInfoBytes = decoder.getRawInfoBytes();

        assertNotNull(extractedInfoBytes);
        assertArrayEquals(rawInfoBytes, extractedInfoBytes);

        Metainfo metainfo = new Metainfo(decodedTorrent, extractedInfoBytes);
        assertEquals("http://tracker.example.com/announce", metainfo.getAnnounce());
        assertEquals("testfile.txt", metainfo.getName());
        assertEquals(262144L, metainfo.getPieceLength());
        assertEquals(2, metainfo.getPieceCount());
        assertEquals(500000L, metainfo.getTotalLength());
        assertFalse(metainfo.isMultiFile());
        assertEquals(1, metainfo.getFiles().size());
        assertEquals(500000L, metainfo.getFiles().get(0).getLength());
    }
}
