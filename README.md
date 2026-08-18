# TorrentX

TorrentX is a high-performance, professional peer-to-peer BitTorrent client built from scratch in Java.

## Technology Stack
- **Language**: Java 21 LTS
- **Build System**: Maven 3.9+
- **GUI Framework**: JavaFX 21 (for future interface implementation)
- **Unit Testing**: JUnit 5, Mockito
- **Logging**: SLF4J with Logback

## Standard Directory Structure
The project follows standard Maven conventions:
```text
TorrentX/
├── pom.xml
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/             # Java source files (com.torrentx)
    │   └── resources/        # Main resources (JavaFX FXML, CSS, Logging configs)
    └── test/
        ├── java/             # Unit tests
        └── resources/        # Test resources
```

## Architectural Package Layout (`com.torrentx`)
To maintain high modularity and separation of concerns, TorrentX is structured into the following packages:
- **`com.torrentx.core`**: Core engine orchestrating components, state machines, and torrent session flow.
- **`com.torrentx.torrent`**: Data structures representing .torrent metadata, tracker responses, and bencode parser.
- **`com.torrentx.tracker`**: Communication protocol handlers for HTTP/UDP trackers.
- **`com.torrentx.peer`**: BitTorrent peer protocol, connection states, choke/interest mechanisms, and handshakes.
- **`com.torrentx.network`**: Low-level TCP and UDP network transport layers.
- **`com.torrentx.storage`**: Random access file storage, piece verification, and multi-file mapping.
- **`com.torrentx.security`**: SHA-1 cryptographic verifications and message digest operations.
- **`com.torrentx.gui`**: User interface components, layout FXMLs, CSS, and interactive controllers (JavaFX).
- **`com.torrentx.utils`**: General helper libraries, data formatting, byte operations, and common constants.

## Build and Run

### Prerequisites
- JDK 21 or higher installed.
- Maven 3.9+ installed and configured on PATH.

### Compile
To clean and compile the project, run:
```bash
mvn clean compile
```

### Run Tests
To run unit tests, run:
```bash
mvn test
```

### Run GUI (JavaFX Entry Point)
To run the JavaFX GUI application, run:
```bash
mvn javafx:run
```
