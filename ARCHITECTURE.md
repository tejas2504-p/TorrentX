# TorrentX Architecture

This document describes the high-level architecture, thread model, and component interactions of TorrentX.

## Core Design Principles
- **Non-Blocking I/O:** Socket communication utilizes Java NIO (`Selector`, `SocketChannel`) to allow a single thread to multiplex hundreds of peer connections.
- **Thread Decoupling:** The JavaFX UI thread is completely isolated from the BitTorrent engine.
- **Defensive Boundary:** All external data (tracker responses, peer socket buffers, `.torrent` files) is structurally validated and hashed before interacting with local storage.

---

## Component Deep-Dive

### 1. PeerManager (NIO Reactor)
The heart of the network layer. 
- Runs on a single dedicated thread (`PeerManager-Reactor`).
- Iterates over `SelectionKey`s for `OP_ACCEPT`, `OP_CONNECT`, `OP_READ`, and `OP_WRITE`.
- Manages connection limits and timeout sweeps via a secondary scheduled thread.

### 2. ProtocolHandler
A stateless packet processor bridging the `PeerManager` and the BitTorrent application logic.
- Maintains the BitTorrent State Machine for each peer (Choked, Unchoked, Interested).
- Dispatches parsed messages (`PieceMessage`, `RequestMessage`, `BitfieldMessage`) into the respective engine managers.

### 3. DownloadManager
The orchestration engine for acquiring files.
- Uses a `ScheduledExecutorService` running a 100ms `downloadTask` loop.
- Scans `PieceAvailability` across all unchoked peers.
- Asks the `PieceSelector` (Rarest-First) and `BlockSelector` for unfulfilled block requests and queues them on the `PeerConnection`'s write buffer.

### 4. Storage & Hashing
- **PieceManager:** Tracks piece states (`EMPTY`, `DOWNLOADING`, `VERIFYING`, `VERIFIED`, `FAILED`). Maintains memory buffers for actively downloading pieces.
- **PieceAssembler:** Once all blocks of a piece arrive, it executes a SHA-1 hash over the buffer and compares it against the `.torrent` metadata hash array.
- **DiskWriter:** A thread-safe file mapping utility that translates absolute piece indices into local multi-file physical offsets, preventing path traversal.

### 5. JavaFX UI & Service Layer
- **TorrentService:** A singleton registry maintaining a map of `ActiveTorrent` instances.
- **ActiveTorrent:** A facade wrapper around a running engine stack (`DownloadManager`, `PeerManager`). It exposes thread-safe, atomic metrics (progress, speeds) specifically designed for the JavaFX `Platform.runLater()` polling mechanisms.

---

## Data Flow: Downloading a Block

1. `DownloadManager` executes its 100ms sweep.
2. Identifies a peer that has a piece we want and is unchoked.
3. Constructs a `RequestMessage` (e.g., Piece 4, Offset 0, Length 16384).
4. `PeerManager`'s NIO reactor writes the request to the socket.
5. The remote peer responds with a `PieceMessage`.
6. `PeerManager` reads the raw bytes.
7. `ProtocolHandler` parses the `PieceMessage` and notifies `BlockSelector`.
8. The block is slotted into `PieceManager`'s memory buffer.
9. If the piece is complete, `DownloadManager` is notified via `PieceCompletionListener`.
10. `PieceAssembler` hashes the piece.
11. If valid, `DiskWriter` flushes the bytes to disk.
