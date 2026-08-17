package com.torrentx.tracker;

import com.torrentx.bencode.Metainfo;
import com.torrentx.peer.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UdpTrackerClient implements TrackerClient {
    private static final Logger logger = LoggerFactory.getLogger(UdpTrackerClient.class);
    private static final long CONNECTION_ID_MAGIC = 0x41727101980L;
    private final Random random = new Random();

    @Override
    public List<Peer> announce(Metainfo metainfo, byte[] peerId, int port, long uploaded, long downloaded, long left, String event) throws Exception {
        URI announceUri;
        try {
            announceUri = new URI(metainfo.getAnnounce());
        } catch (URISyntaxException e) {
            throw new IOException("Invalid announce URI", e);
        }

        if (!"udp".equalsIgnoreCase(announceUri.getScheme())) {
            throw new IllegalArgumentException("Only UDP scheme is supported by this client");
        }

        String host = announceUri.getHost();
        int trackerPort = announceUri.getPort();
        if (trackerPort == -1) {
            trackerPort = 80;
        }

        InetAddress address = InetAddress.getByName(host);
        InetSocketAddress trackerAddress = new InetSocketAddress(address, trackerPort);

        logger.info("Connecting to UDP tracker: {}:{}", host, trackerPort);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);

            int transactionId = random.nextInt();
            ByteBuffer connectRequest = ByteBuffer.allocate(16);
            connectRequest.putLong(CONNECTION_ID_MAGIC);
            connectRequest.putInt(0);
            connectRequest.putInt(transactionId);

            byte[] sendBuffer = connectRequest.array();
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, trackerAddress);
            socket.send(sendPacket);

            byte[] receiveBuffer = new byte[16];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            ByteBuffer connectResponse = ByteBuffer.wrap(receiveBuffer);
            int action = connectResponse.getInt();
            int respTransactionId = connectResponse.getInt();
            
            if (action == 3) {
                byte[] errorBuffer = new byte[1024];
                DatagramPacket errPacket = new DatagramPacket(errorBuffer, errorBuffer.length);
                socket.receive(errPacket);
                String errMsg = new String(errorBuffer, 0, errPacket.getLength());
                throw new IOException("Tracker connect error: " + errMsg);
            }

            if (respTransactionId != transactionId) {
                throw new IOException("Transaction ID mismatch on connect response");
            }
            if (action != 0) {
                throw new IOException("Invalid action in connect response: " + action);
            }

            long connectionId = connectResponse.getLong();
            logger.info("Connected to UDP tracker. Connection ID: {}", connectionId);

            int announceTransactionId = random.nextInt();
            ByteBuffer announceRequest = ByteBuffer.allocate(98);
            announceRequest.putLong(connectionId);
            announceRequest.putInt(1);
            announceRequest.putInt(announceTransactionId);
            announceRequest.put(metainfo.getInfoHash());
            announceRequest.put(peerId);
            announceRequest.putLong(downloaded);
            announceRequest.putLong(left);
            announceRequest.putLong(uploaded);
            
            int eventVal = 0;
            if ("completed".equalsIgnoreCase(event)) eventVal = 1;
            else if ("started".equalsIgnoreCase(event)) eventVal = 2;
            else if ("stopped".equalsIgnoreCase(event)) eventVal = 3;
            announceRequest.putInt(eventVal);
            
            announceRequest.putInt(0);
            announceRequest.putInt(random.nextInt());
            announceRequest.putInt(-1);
            announceRequest.putShort((short) port);

            byte[] announceSendBuffer = announceRequest.array();
            DatagramPacket announceSendPacket = new DatagramPacket(announceSendBuffer, announceSendBuffer.length, trackerAddress);
            socket.send(announceSendPacket);

            byte[] announceReceiveBuffer = new byte[20 + 300 * 6];
            DatagramPacket announceReceivePacket = new DatagramPacket(announceReceiveBuffer, announceReceiveBuffer.length);
            socket.receive(announceReceivePacket);

            ByteBuffer announceResponse = ByteBuffer.wrap(announceReceiveBuffer, 0, announceReceivePacket.getLength());
            int respAction = announceResponse.getInt();
            int respAnnounceTxId = announceResponse.getInt();

            if (respAction == 3) {
                String errMsg = new String(announceReceiveBuffer, 8, announceReceivePacket.getLength() - 8);
                throw new IOException("Tracker announce error: " + errMsg);
            }

            if (respAnnounceTxId != announceTransactionId) {
                throw new IOException("Transaction ID mismatch on announce response");
            }
            if (respAction != 1) {
                throw new IOException("Invalid action in announce response: " + respAction);
            }

            int interval = announceResponse.getInt();
            int leechers = announceResponse.getInt();
            int seeders = announceResponse.getInt();

            int peersLength = announceReceivePacket.getLength() - 20;
            if (peersLength % 6 != 0) {
                throw new IOException("UDP announce response peers length must be multiple of 6");
            }

            int peerCount = peersLength / 6;
            List<Peer> peers = new ArrayList<>(peerCount);
            for (int i = 0; i < peerCount; i++) {
                int offset = 20 + i * 6;
                String ip = String.format("%d.%d.%d.%d",
                        announceReceiveBuffer[offset] & 0xFF,
                        announceReceiveBuffer[offset + 1] & 0xFF,
                        announceReceiveBuffer[offset + 2] & 0xFF,
                        announceReceiveBuffer[offset + 3] & 0xFF);
                int peerPort = ((announceReceiveBuffer[offset + 4] & 0xFF) << 8) | (announceReceiveBuffer[offset + 5] & 0xFF);
                peers.add(new Peer("", ip, peerPort));
            }

            logger.info("Found {} peers from UDP tracker (seeders: {}, leechers: {})", peers.size(), seeders, leechers);
            return peers;
        }
    }
}
