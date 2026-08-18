package com.torrentx.gui;

import javafx.beans.property.*;

public class TorrentRow {
    private final StringProperty name;
    private final StringProperty size;
    private final DoubleProperty progress;
    private final StringProperty status;
    private final StringProperty downSpeed;
    private final StringProperty upSpeed;
    private final StringProperty peers;

    public TorrentRow(String name, String size, double progress, String status, String downSpeed, String upSpeed, String peers) {
        this.name = new SimpleStringProperty(name);
        this.size = new SimpleStringProperty(size);
        this.progress = new SimpleDoubleProperty(progress);
        this.status = new SimpleStringProperty(status);
        this.downSpeed = new SimpleStringProperty(downSpeed);
        this.upSpeed = new SimpleStringProperty(upSpeed);
        this.peers = new SimpleStringProperty(peers);
    }

    public StringProperty nameProperty() { return name; }
    public StringProperty sizeProperty() { return size; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty statusProperty() { return status; }
    public StringProperty downSpeedProperty() { return downSpeed; }
    public StringProperty upSpeedProperty() { return upSpeed; }
    public StringProperty peersProperty() { return peers; }
}
