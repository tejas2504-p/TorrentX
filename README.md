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
- **`com.torrentx.bencode`**: Bencode parser, encoder, and metainfo (.torrent) file decoders.
- **`com.torrentx.core`**: Core torrent engine, piece management, selection algorithms, and state persistence.
- **`com.torrentx.tracker`**: Communication clients for HTTP and UDP trackers to coordinate peer finding.
- **`com.torrentx.peer`**: Peer networking services, handshakes, TCP connection management, and wire message protocol handler.
- **`com.torrentx.storage`**: File management system, storage allocation, block writes/reads, and verification of integrity.
- **`com.torrentx.ui`**: Graphical user interface components, views, layouts, and controllers using JavaFX.

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
