# Contributing to TorrentX

Thank you for your interest in contributing to TorrentX! This document provides guidelines for developers who want to extend the project.

## Development Setup

1. **Clone the repo.**
2. **Import into your IDE:** Use IntelliJ IDEA, Eclipse, or VSCode. The project uses standard Maven `pom.xml`.
3. **Java Version:** Ensure you are using JDK 21.

## Coding Standards

- **Thread Safety:** When touching the `PeerManager`, remember it runs in an asynchronous NIO loop. Avoid introducing blocking calls (like `Thread.sleep` or heavy Disk I/O) into the `PeerManager-Reactor` thread.
- **Zero-Copy / Low Allocation:** When parsing peer messages, use `ByteBuffer.slice()` and `ByteBuffer.wrap()` instead of allocating new `byte[]` arrays to prevent Garbage Collector pressure.
- **JavaFX Thread:** Never execute network or disk operations directly inside `MainController` or `SettingsController`. Dispatch work to the `TorrentService` backend.

## Testing Strategy

TorrentX heavily relies on automated testing. Before submitting a Pull Request, ensure:
```bash
mvn clean test
```
returns 100% success.

### Writing Tests
- **Unit Tests:** Mock dependencies using Mockito.
- **NIO Mocks:** Testing the `ProtocolHandler` or `MessageCodec` usually involves wrapping raw hex bytes into a `ByteBuffer` and asserting state transitions, avoiding the need to spin up actual loopback sockets unless running an Integration Test.
- **File System Tests:** Always use Java NIO `Files.createTempDirectory()` for `DiskWriter` tests to avoid cluttering local disks or running into permission errors.

## Roadmap & Good First Issues
If you are looking for somewhere to start, please look at the **Partially Implemented** and **Future Work** sections in the `README.md`.
Specifically:
- Implementing the Token Bucket algorithm in `DownloadManager` for rate limiting.
- Expanding `ProtocolHandler` to service incoming `RequestMessage` block uploads.
- Adding a TestFX test harness for the GUI.
