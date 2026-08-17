package com.torrentx.peer;

import com.torrentx.bencode.Metainfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Queue;

public class PeerConnection {
    private static final Logger logger = LoggerFactory.getLogger(PeerConnection.class);
    
    private final Peer peer;
    private final SocketChannel socketChannel;
    private final Metainfo metainfo;
    
    private volatile boolean handshakeSent = false;
    private volatile boolean handshakeReceived = false;
    private volatile boolean peerChoking = true;
    private volatile boolean peerInterested = false;
    private volatile boolean amChoking = true;
    private volatile boolean amInterested = false;
    
    private final BitSet peerBitfield;
    private final Queue<ByteBuffer> sendQueue = new LinkedList<>();

    private final ByteBuffer readBuffer = ByteBuffer.allocate(65536);
    private final ByteBuffer msgBuffer = ByteBuffer.allocate(262144);
    
    public PeerConnection(Peer peer, SocketChannel socketChannel, Metainfo metainfo) {
        this.peer = peer;
        this.socketChannel = socketChannel;
        this.metainfo = metainfo;
        this.peerBitfield = new BitSet(metainfo.getPieceCount());
        this.msgBuffer.limit(0);
    }

    public Peer getPeer() {
        return peer;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public boolean isHandshakeSent() {
        return handshakeSent;
    }

    public boolean isHandshakeReceived() {
        return handshakeReceived;
    }

    public boolean isPeerChoking() {
        return peerChoking;
    }

    public boolean isPeerInterested() {
        return peerInterested;
    }

    public boolean isAmChoking() {
        return amChoking;
    }

    public boolean isAmInterested() {
        return amInterested;
    }

    public void setAmInterested(boolean interested) {
        this.amInterested = interested;
    }
    
    public void setAmChoking(boolean choking) {
        this.amChoking = choking;
    }

    public synchronized boolean hasPiece(int pieceIndex) {
        return peerBitfield.get(pieceIndex);
    }
    
    public synchronized BitSet getPeerBitfield() {
        return (BitSet) peerBitfield.clone();
    }

    public synchronized void queueMessage(PeerWireMessage message) {
        sendQueue.add(ByteBuffer.wrap(message.serialize()));
    }

    public synchronized void sendHandshake(byte[] localPeerId) throws IOException {
        if (handshakeSent) return;

        ByteBuffer handshake = ByteBuffer.allocate(68);
        handshake.put((byte) 19);
        handshake.put("BitTorrent protocol".getBytes(StandardCharsets.US_ASCII));
        handshake.putLong(0L);
        handshake.put(metainfo.getInfoHash());
        handshake.put(localPeerId);
        handshake.flip();

        sendQueue.add(handshake);
        handshakeSent = true;
        logger.info("Queued handshake for peer {}", peer);
    }

    public synchronized void write() throws IOException {
        while (!sendQueue.isEmpty()) {
            ByteBuffer buffer = sendQueue.peek();
            socketChannel.write(buffer);
            if (buffer.hasRemaining()) {
                break;
            }
            sendQueue.poll();
        }
    }

    public boolean readAndProcess(PeerConnectionListener listener) throws IOException {
        readBuffer.clear();
        int bytesRead = socketChannel.read(readBuffer);
        if (bytesRead == -1) {
            return true;
        }
        if (bytesRead == 0) {
            return false;
        }

        readBuffer.flip();
        msgBuffer.compact();
        msgBuffer.put(readBuffer);
        msgBuffer.flip();

        while (true) {
            if (!handshakeReceived) {
                if (msgBuffer.remaining() < 68) {
                    break;
                }
                processHandshake(listener);
            } else {
                if (msgBuffer.remaining() < 4) {
                    break;
                }
                msgBuffer.mark();
                int length = msgBuffer.getInt();
                
                if (length < 0 || length > 262144) {
                    throw new IOException("Protocol violation: invalid message length " + length);
                }

                if (length == 0) {
                    listener.onKeepAlive(this);
                    continue;
                }

                if (msgBuffer.remaining() < length) {
                    msgBuffer.reset();
                    break;
                }

                byte id = msgBuffer.get();
                byte[] payload = new byte[length - 1];
                msgBuffer.get(payload);

                PeerWireMessage message = new PeerWireMessage(id, payload);
                processMessage(message, listener);
            }
        }
        return false;
    }

    private void processHandshake(PeerConnectionListener listener) throws IOException {
        byte pstrlen = msgBuffer.get();
        if (pstrlen != 19) {
            throw new IOException("Invalid handshake pstrlen: " + pstrlen);
        }
        byte[] pstrBytes = new byte[19];
        msgBuffer.get(pstrBytes);
        String pstr = new String(pstrBytes, StandardCharsets.US_ASCII);
        if (!"BitTorrent protocol".equals(pstr)) {
            throw new IOException("Invalid protocol identifier: " + pstr);
        }

        byte[] reserved = new byte[8];
        msgBuffer.get(reserved);

        byte[] infoHash = new byte[20];
        msgBuffer.get(infoHash);
        if (!Arrays.equals(infoHash, metainfo.getInfoHash())) {
            throw new IOException("Handshake info hash mismatch!");
        }

        byte[] peerId = new byte[20];
        msgBuffer.get(peerId);

        handshakeReceived = true;
        logger.info("Received valid handshake from peer {}", peer);
        listener.onHandshake(this, peerId);
    }

    private void processMessage(PeerWireMessage msg, PeerConnectionListener listener) throws IOException {
        switch (msg.getId()) {
            case 0:
                peerChoking = true;
                logger.info("Peer {} choked us", peer);
                listener.onChoke(this);
                break;
            case 1:
                peerChoking = false;
                logger.info("Peer {} unchoked us", peer);
                listener.onUnchoke(this);
                break;
            case 2:
                peerInterested = true;
                logger.info("Peer {} is interested in us", peer);
                listener.onInterested(this);
                break;
            case 3:
                peerInterested = false;
                logger.info("Peer {} is not interested in us", peer);
                listener.onNotInterested(this);
                break;
            case 4:
                if (msg.getPayload().length != 4) {
                    throw new IOException("Have message payload must be 4 bytes");
                }
                int pieceIndex = ByteBuffer.wrap(msg.getPayload()).getInt();
                if (pieceIndex >= 0 && pieceIndex < metainfo.getPieceCount()) {
                    synchronized (this) {
                        peerBitfield.set(pieceIndex);
                    }
                    listener.onHave(this, pieceIndex);
                }
                break;
            case 5:
                byte[] bitfieldBytes = msg.getPayload();
                int expectedBytes = (metainfo.getPieceCount() + 7) / 8;
                if (bitfieldBytes.length != expectedBytes) {
                    throw new IOException("Bitfield size mismatch: expected " + expectedBytes + " bytes, got " + bitfieldBytes.length);
                }
                synchronized (this) {
                    peerBitfield.clear();
                    for (int i = 0; i < metainfo.getPieceCount(); i++) {
                        int byteIdx = i / 8;
                        int bitIdx = 7 - (i % 8);
                        boolean hasPiece = ((bitfieldBytes[byteIdx] >> bitIdx) & 0x01) == 1;
                        if (hasPiece) {
                            peerBitfield.set(i);
                        }
                    }
                }
                listener.onBitfield(this, getPeerBitfield());
                break;
            case 6:
                if (msg.getPayload().length != 12) {
                    throw new IOException("Request message payload must be 12 bytes");
                }
                ByteBuffer reqBuf = ByteBuffer.wrap(msg.getPayload());
                int reqPiece = reqBuf.getInt();
                int reqBegin = reqBuf.getInt();
                int reqLength = reqBuf.getInt();
                listener.onRequest(this, reqPiece, reqBegin, reqLength);
                break;
            case 7:
                byte[] payload = msg.getPayload();
                if (payload.length < 8) {
                    throw new IOException("Piece message payload must be at least 8 bytes");
                }
                ByteBuffer pieceBuf = ByteBuffer.wrap(payload);
                int pIndex = pieceBuf.getInt();
                int pBegin = pieceBuf.getInt();
                byte[] block = new byte[payload.length - 8];
                System.arraycopy(payload, 8, block, 0, block.length);
                listener.onPiece(this, pIndex, pBegin, block);
                break;
            case 8:
                if (msg.getPayload().length != 12) {
                    throw new IOException("Cancel message payload must be 12 bytes");
                }
                ByteBuffer cancelBuf = ByteBuffer.wrap(msg.getPayload());
                int cancelPiece = cancelBuf.getInt();
                int cancelBegin = cancelBuf.getInt();
                int cancelLength = cancelBuf.getInt();
                listener.onCancel(this, cancelPiece, cancelBegin, cancelLength);
                break;
            default:
                logger.warn("Unknown message ID received from peer {}: {}", peer, msg.getId());
        }
    }
    
    public interface PeerConnectionListener {
        void onHandshake(PeerConnection conn, byte[] remotePeerId);
        void onChoke(PeerConnection conn);
        void onUnchoke(PeerConnection conn);
        void onInterested(PeerConnection conn);
        void onNotInterested(PeerConnection conn);
        void onHave(PeerConnection conn, int pieceIndex);
        void onBitfield(PeerConnection conn, BitSet bitfield);
        void onRequest(PeerConnection conn, int pieceIndex, int begin, int length);
        void onPiece(PeerConnection conn, int pieceIndex, int begin, byte[] block);
        void onCancel(PeerConnection conn, int pieceIndex, int begin, int length);
        void onKeepAlive(PeerConnection conn);
    }
}
