# TorrentX

TorrentX is a high-performance, professional peer-to-peer BitTorrent client built from scratch in Java 21.

## 1. Project Overview
TorrentX is designed to be a highly modular, clean, and efficient desktop BitTorrent client. The core engine coordinates torrent lifecycle states, network transport, disk storage, hash verification, and peer communication, all fronted by a modern, premium desktop user interface.

## 2. Problem Statement
Many mainstream BitTorrent clients have become bloated, closed-source, resource-intensive, or cluttered with advertisements. Furthermore, many open-source clients lack a clear, modern separation of concerns, making it difficult for developers to learn from, extend, or audit the codebase for security. TorrentX addresses these problems by providing a clean-room, robust, fully documented, and strictly modular implementation in Java.

## 3. Project Objectives
- **Strict Modularity**: Maintain a clean architecture with low coupling between networking, storage, parsing, and UI layers.
- **Resource Efficiency**: Use high-performance socket operations and random access disk storage to minimize CPU and RAM footprints.
- **Robust Security**: Implement rigorous piece verification using SHA-1 cryptographic digests before storing files.
- **Rich Developer Foundation**: Provide 100% test coverage for configurations, utilities, and core models to ensure reliability.

## 4. Planned Features
- Full support for Bencode decoding and .torrent file parsing.
- Dynamic tracker announcements via HTTP and UDP protocols.
- BitTorrent peer wire protocol communication, including choke/interest handshakes.
- Multi-file torrent downloads mapped directly to disk storage.
- Premium JavaFX desktop GUI with real-time speed, progress, and peer metrics.

## 5. Technology Stack
- **Language**: Java 21 LTS
- **Build System**: Maven 3.9+
- **GUI Framework**: JavaFX 21
- **Unit Testing**: JUnit 5, Mockito
- **Logging**: SLF4J with Logback

## 6. Architecture Overview
TorrentX is structured into separate, decoupled packages to support high modularity:
- **`com.torrentx.core`**: Core engine orchestrating components, state machines, and torrent session flow.
- **`com.torrentx.torrent`**: Data structures representing .torrent metadata and bencode parsing.
- **`com.torrentx.tracker`**: Communication protocol handlers for HTTP/UDP trackers.
- **`com.torrentx.peer`**: BitTorrent peer protocol, connection states, choke/interest mechanisms, and handshakes.
- **`com.torrentx.network`**: Low-level TCP and UDP network transport layers.
- **`com.torrentx.storage`**: Random access file storage, piece verification, and multi-file mapping.
- **`com.torrentx.security`**: SHA-1 cryptographic verifications and message digest operations.
- **`com.torrentx.gui`**: User interface components, layout FXMLs, CSS, and interactive controllers (JavaFX).
- **`com.torrentx.utils`**: General helper libraries, data formatting, configurations, and wrapper loggers.

## 7. Current Project Structure
The directories match standard Maven project structures:
```text
TorrentX/
├── pom.xml
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/             # Source packages
    │   │   └── com/torrentx/
    │   │       ├── TorrentClient.java # Main application entry point
    │   │       ├── bencode/  # Bencode placeholders
    │   │       ├── core/     # Core lifecycle (ClientManager)
    │   │       ├── gui/      # MainWindow, MainController, TorrentRow, TorrentXApp
    │   │       ├── network/  # Message, ProtocolHandler, SocketManager
    │   │       ├── peer/     # Peer, PeerConnection, PeerManager
    │   │       ├── security/ # HashVerifier
    │   │       ├── storage/  # FileManager, PieceStorage
    │   │       ├── torrent/  # BencodeDecoder, TorrentMetadata, TorrentParser
    │   │       ├── tracker/  # PeerInfo, TrackerClient
    │   │       └── utils/    # Config, Logger
    │   └── resources/        # Configuration and FXML layouts
    │       ├── config.properties
    │       ├── logback.xml
    │       └── ui/           # main.fxml, main.css
    └── test/
        └── java/             # Complete matching unit test suite
            └── com/torrentx/
                ├── core/     # ClientManagerTest
                ├── gui/      # TorrentRowTest
                ├── network/  # MessageTest
                ├── peer/     # PeerTest
                ├── storage/  # FileManagerTest
                ├── torrent/  # TorrentMetadataTest
                ├── tracker/  # PeerInfoTest
                └── utils/    # ConfigTest, LoggerTest
```

## 8. Current Project Status
### **Phase 1 — Project Setup and Architecture (Completed)**
All core packages, class files, and interface structures representing the TorrentX architecture have been successfully created and linked. 
- **Configuration Foundation**: Integrated `config.properties` loading with validation constraints.
- **Logging Foundation**: Integrated logback wrapper for Info/Debug/Warn/Error configurations.
- **Testing Foundation**: Structured JUnit 5 tests covering all core initializers and data structures.

## 9. Development Roadmap
- **Phase 1 — Project Setup and Architecture** (Completed)
- **Phase 2 — Torrent Parsing and Bencode Decoding** (Planned)
- **Phase 3 — Network Transport and Peer Wire Protocol** (Planned)
- **Phase 4 — Storage, File Mapping, and Verification** (Planned)
- **Phase 5 — GUI Desktop Application and Final Release** (Planned)

## 10. Build Instructions
To clean and compile the project, run:
```bash
mvn clean compile
```

## 11. Test Instructions
To run the full test suite (26 unit tests):
```bash
mvn test
```

## 12. Future Enhancements
- Distributed Hash Table (DHT) support for trackerless torrents.
- Peer Exchange (PEX) and Magnet link support.
- Encrypted peer protocol handshakes (MSE/PE).
- Granular speed limiting and disk caching.
