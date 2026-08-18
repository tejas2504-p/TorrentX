package com.torrentx.storage;

import java.io.File;
import java.io.IOException;

/**
 * Manages target files on disk, handles file allocations, and maps block writing/reading ranges to correct files.
 */
public class FileManager {

    private final File downloadDirectory;

    /**
     * Constructs a FileManager.
     */
    public FileManager(File downloadDirectory) {
        if (downloadDirectory == null) {
            throw new IllegalArgumentException("Download directory cannot be null");
        }
        this.downloadDirectory = downloadDirectory;
    }

    /**
     * Pre-allocates necessary files and directories on disk based on total lengths.
     *
     * @throws IOException if disk allocation fails.
     */
    public void allocateFiles() throws IOException {
        // TODO: Implement file and folder structures allocation in future phases
        throw new UnsupportedOperationException("File allocation is not implemented yet.");
    }

    /**
     * Closes any open file channels or resource streams.
     *
     * @throws IOException if closing channels fails.
     */
    public void close() throws IOException {
        // TODO: Implement file channel close procedures in future phases
        throw new UnsupportedOperationException("FileManager resource cleanups are not implemented yet.");
    }

    public File getDownloadDirectory() {
        return downloadDirectory;
    }
}
