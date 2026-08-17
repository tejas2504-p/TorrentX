package com.torrentx.core;

import com.torrentx.bencode.Metainfo;
import com.torrentx.peer.Peer;
import com.torrentx.peer.PeerConnection;
import com.torrentx.peer.PeerService;
import com.torrentx.peer.PeerWireMessage;
import com.torrentx.storage.StorageService;
import com.torrentx.tracker.HttpTrackerClient;
import com.torrentx.tracker.UdpTrackerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class TorrentEngine implements PeerConnection.PeerConnectionListener {
    private static final Logger logger = LoggerFactory.getLogger(TorrentEngine.class);

    private final Metainfo metainfo;
    private final byte[] localPeerId;
    private final int localPort;
    private final File downloadDir;

    private final StorageService storageManager;
    private final PeerService peerManager;
    private final PieceSelectionStrategy pieceSelector;

    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService trackerScheduler = Executors.newSingleThreadScheduledExecutor();

    private final BitSet myBitfield;
    private final Map<Integer, ActivePiece> activePieces = new ConcurrentHashMap<>();
    private final Map<PeerConnection, Set<Integer>> pendingRequests = new ConcurrentHashMap<>();

    private long bytesUploaded = 0;
    private long bytesDownloaded = 0;

    private volatile boolean running = false;
    private final TorrentStateListener stateListener;

    public interface TorrentStateListener {
        void onProgressUpdate(double progress, double downloadSpeed, double uploadSpeed, int activePeers);
        void onTorrentComplete();
        void onError(String message);
    }

    public TorrentEngine(Metainfo metainfo, File downloadDir, int localPort,
                         StorageService storageManager, PeerService peerManager, PieceSelectionStrategy pieceSelector,
                         TorrentStateListener stateListener) {
        this.metainfo = metainfo;
        this.downloadDir = downloadDir;
        this.localPort = localPort;
        this.stateListener = stateListener;

        this.localPeerId = generatePeerId();
        this.myBitfield = new BitSet(metainfo.getPieceCount());
        
        this.storageManager = storageManager;
        this.peerManager = peerManager;
        this.pieceSelector = pieceSelector;
    }

    public static byte[] generatePeerId() {
        byte[] id = new byte[20];
        byte[] prefix = "-TX0001-".getBytes();
        System.arraycopy(prefix, 0, id, 0, prefix.length);
        Random random = new Random();
        for (int i = prefix.length; i < 20; i++) {
            id[i] = (byte) ('0' + random.nextInt(10));
        }
        return id;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        running = true;

        logger.info("Initializing storage space for torrent {}", metainfo.getName());
        storageManager.initialize();

        diskExecutor.submit(this::performStartupHashCheck);
        peerManager.start();
        trackerScheduler.scheduleWithFixedDelay(this::announceToTracker, 0, 300, TimeUnit.SECONDS);

        logger.info("TorrentEngine started for {}", metainfo.getName());
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        
        peerManager.stop();
        trackerScheduler.shutdown();
        diskExecutor.shutdown();
        try {
            storageManager.close();
        } catch (IOException e) {
            logger.error("Failed to close storage service", e);
        }
        logger.info("TorrentEngine stopped");
    }

    public double getProgress() {
        int piecesHave = myBitfield.cardinality();
        return (double) piecesHave / metainfo.getPieceCount();
    }

    private void performStartupHashCheck() {
        logger.info("Performing startup hash verification...");
        int completed = 0;
        for (int i = 0; i < metainfo.getPieceCount(); i++) {
            if (storageManager.verifyPiece(i)) {
                myBitfield.set(i);
                completed++;
            }
        }
        logger.info("Startup hash check complete. Found {}/{} completed pieces.", completed, metainfo.getPieceCount());
        triggerStateUpdate();
        if (completed == metainfo.getPieceCount()) {
            if (stateListener != null) {
                stateListener.onTorrentComplete();
            }
        }
    }

    private void announceToTracker() {
        try {
            List<Peer> peers;
            long left = metainfo.getTotalLength() - (myBitfield.cardinality() * metainfo.getPieceLength());
            if (left < 0) left = 0;

            String event = (left == 0) ? "completed" : "started";

            if (metainfo.getAnnounce().startsWith("udp://")) {
                UdpTrackerClient trackerClient = new UdpTrackerClient();
                peers = trackerClient.announce(metainfo, localPeerId, localPort, bytesUploaded, bytesDownloaded, left, event);
            } else {
                HttpTrackerClient trackerClient = new HttpTrackerClient();
                peers = trackerClient.announce(metainfo, localPeerId, localPort, bytesUploaded, bytesDownloaded, left, event);
            }

            peerManager.connectToPeers(peers);
        } catch (Exception e) {
            logger.error("Failed to announce to tracker", e);
        }
    }

    private void triggerStateUpdate() {
        if (stateListener != null) {
            double progress = getProgress();
            stateListener.onProgressUpdate(progress, 0.0, 0.0, peerManager.getActiveConnections().size());
        }
    }

    @Override
    public void onHandshake(PeerConnection conn, byte[] remotePeerId) {
        if (myBitfield.cardinality() > 0) {
            byte[] bitfieldBytes = new byte[(metainfo.getPieceCount() + 7) / 8];
            for (int i = 0; i < metainfo.getPieceCount(); i++) {
                if (myBitfield.get(i)) {
                    int byteIdx = i / 8;
                    int bitIdx = 7 - (i % 8);
                    bitfieldBytes[byteIdx] |= (1 << bitIdx);
                }
            }
            conn.queueMessage(PeerWireMessage.bitfield(bitfieldBytes));
        }

        checkInterest(conn);
    }

    private void checkInterest(PeerConnection conn) {
        boolean interested = false;
        BitSet peerField = conn.getPeerBitfield();
        for (int i = 0; i < metainfo.getPieceCount(); i++) {
            if (peerField.get(i) && !myBitfield.get(i)) {
                interested = true;
                break;
            }
        }

        if (interested != conn.isAmInterested()) {
            conn.setAmInterested(interested);
            if (interested) {
                conn.queueMessage(PeerWireMessage.interested());
            } else {
                conn.queueMessage(PeerWireMessage.notInterested());
            }
        }
    }

    @Override
    public void onChoke(PeerConnection conn) {
        Set<Integer> pending = pendingRequests.get(conn);
        if (pending != null) {
            synchronized (pending) {
                for (int blockIdx : pending) {
                    for (ActivePiece ap : activePieces.values()) {
                        ap.resetRequest(blockIdx);
                    }
                }
                pending.clear();
            }
        }
    }

    @Override
    public void onUnchoke(PeerConnection conn) {
        requestMoreBlocks(conn);
    }

    private void requestMoreBlocks(PeerConnection conn) {
        if (conn.isPeerChoking() || !conn.isAmInterested()) {
            return;
        }

        int pieceIdx = pieceSelector.selectPiece(conn, myBitfield, activePieces, peerManager.getActiveConnections());
        if (pieceIdx == -1) {
            for (ActivePiece ap : activePieces.values()) {
                if (conn.hasPiece(ap.getIndex())) {
                    requestBlocksFromActivePiece(conn, ap);
                    return;
                }
            }
            return;
        }

        int pieceLen = (int) storageManager.getPieceLength(pieceIdx);
        ActivePiece activePiece = new ActivePiece(pieceIdx, pieceLen, 16384);
        activePieces.put(pieceIdx, activePiece);

        requestBlocksFromActivePiece(conn, activePiece);
    }

    private void requestBlocksFromActivePiece(PeerConnection conn, ActivePiece ap) {
        Set<Integer> pending = pendingRequests.computeIfAbsent(conn, k -> Collections.synchronizedSet(new HashSet<>()));

        while (pending.size() < 5) {
            int nextBlockIdx = -1;
            for (int i = 0; i < ap.getTotalBlocks(); i++) {
                if (!ap.isRequested(i) && !ap.isReceived(i)) {
                    nextBlockIdx = i;
                    break;
                }
            }

            if (nextBlockIdx == -1) {
                break;
            }

            ap.markRequested(nextBlockIdx);
            pending.add(nextBlockIdx);

            int offset = ap.getBlockOffset(nextBlockIdx);
            int size = ap.getBlockSize(nextBlockIdx);

            conn.queueMessage(PeerWireMessage.request(ap.getIndex(), offset, size));
        }
    }

    @Override
    public void onInterested(PeerConnection conn) {
        conn.setAmChoking(false);
        conn.queueMessage(PeerWireMessage.unchoke());
    }

    @Override
    public void onNotInterested(PeerConnection conn) {
        conn.setAmChoking(true);
        conn.queueMessage(PeerWireMessage.choke());
    }

    @Override
    public void onHave(PeerConnection conn, int pieceIndex) {
        checkInterest(conn);
        if (conn.isAmInterested() && !conn.isPeerChoking()) {
            requestMoreBlocks(conn);
        }
    }

    @Override
    public void onBitfield(PeerConnection conn, BitSet bitfield) {
        checkInterest(conn);
        if (conn.isAmInterested() && !conn.isPeerChoking()) {
            requestMoreBlocks(conn);
        }
    }

    @Override
    public void onRequest(PeerConnection conn, int pieceIndex, int begin, int length) {
        diskExecutor.submit(() -> {
            try {
                byte[] data = storageManager.readBlock(pieceIndex, begin, length);
                conn.queueMessage(PeerWireMessage.piece(pieceIndex, begin, data));
                bytesUploaded += length;
                triggerStateUpdate();
            } catch (IOException e) {
                logger.error("Failed to read block from disk: piece={}, begin={}", pieceIndex, begin, e);
            }
        });
    }

    @Override
    public void onPiece(PeerConnection conn, int pieceIndex, int begin, byte[] block) {
        diskExecutor.submit(() -> {
            try {
                storageManager.writeBlock(pieceIndex, begin, block);
                bytesDownloaded += block.length;
                
                ActivePiece ap = activePieces.get(pieceIndex);
                if (ap != null) {
                    int blockIdx = begin / 16384;
                    ap.markReceived(blockIdx);

                    Set<Integer> pending = pendingRequests.get(conn);
                    if (pending != null) {
                        pending.remove(blockIdx);
                    }

                    if (ap.isComplete()) {
                        boolean verified = storageManager.verifyPiece(pieceIndex);
                        if (verified) {
                            logger.info("Successfully verified piece index {}", pieceIndex);
                            myBitfield.set(pieceIndex);
                            activePieces.remove(pieceIndex);

                            for (PeerConnection activeConn : peerManager.getActiveConnections()) {
                                if (activeConn.isHandshakeReceived()) {
                                    activeConn.queueMessage(PeerWireMessage.have(pieceIndex));
                                }
                            }

                            triggerStateUpdate();

                            if (myBitfield.cardinality() == metainfo.getPieceCount()) {
                                logger.info("Download completed successfully!");
                                if (stateListener != null) {
                                    stateListener.onTorrentComplete();
                                }
                            }
                        } else {
                            logger.warn("Piece verification failed for index {}, redownloading...", pieceIndex);
                            ap.reset();
                            activePieces.remove(pieceIndex);
                        }
                    }
                }
                
                requestMoreBlocks(conn);
            } catch (IOException e) {
                logger.error("Failed to write downloaded block to disk", e);
            }
        });
    }

    @Override
    public void onCancel(PeerConnection conn, int pieceIndex, int begin, int length) {
    }

    @Override
    public void onKeepAlive(PeerConnection conn) {
    }
}
