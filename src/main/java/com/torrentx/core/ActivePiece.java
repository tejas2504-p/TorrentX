package com.torrentx.core;

public class ActivePiece {
    private final int index;
    private final int length;
    private final int blockLength;
    private final int totalBlocks;
    
    private final boolean[] blockReceived;
    private final boolean[] blockRequested;
    private int receivedCount = 0;

    public ActivePiece(int index, int length, int blockLength) {
        this.index = index;
        this.length = length;
        this.blockLength = blockLength;
        this.totalBlocks = (int) Math.ceil((double) length / blockLength);
        this.blockReceived = new boolean[totalBlocks];
        this.blockRequested = new boolean[totalBlocks];
    }

    public int getIndex() {
        return index;
    }

    public int getLength() {
        return length;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getBlockOffset(int blockIndex) {
        return blockIndex * blockLength;
    }

    public int getBlockSize(int blockIndex) {
        if (blockIndex == totalBlocks - 1) {
            int remainder = length % blockLength;
            return remainder == 0 ? blockLength : remainder;
        }
        return blockLength;
    }

    public synchronized boolean isComplete() {
        return receivedCount == totalBlocks;
    }

    public synchronized void markRequested(int blockIndex) {
        if (blockIndex >= 0 && blockIndex < totalBlocks) {
            blockRequested[blockIndex] = true;
        }
    }

    public synchronized boolean isRequested(int blockIndex) {
        return blockRequested[blockIndex];
    }

    public synchronized void resetRequest(int blockIndex) {
        if (blockIndex >= 0 && blockIndex < totalBlocks) {
            blockRequested[blockIndex] = false;
        }
    }

    public synchronized boolean isReceived(int blockIndex) {
        return blockReceived[blockIndex];
    }

    public synchronized void markReceived(int blockIndex) {
        if (blockIndex >= 0 && blockIndex < totalBlocks && !blockReceived[blockIndex]) {
            blockReceived[blockIndex] = true;
            receivedCount++;
        }
    }

    public synchronized void reset() {
        for (int i = 0; i < totalBlocks; i++) {
            blockReceived[i] = false;
            blockRequested[i] = false;
        }
        receivedCount = 0;
    }
}
