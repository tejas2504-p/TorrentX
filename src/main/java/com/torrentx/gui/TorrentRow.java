package com.torrentx.gui;

import javafx.beans.property.*;

public class TorrentRow {
    private final StringProperty name;
    private final StringProperty status;
    private final DoubleProperty progress;
    private final StringProperty downSpeed;
    private final StringProperty upSpeed;
    private final StringProperty downloaded;
    private final StringProperty uploaded;
    private final StringProperty size;
    private final StringProperty eta;
    private final StringProperty peers;

    public TorrentRow(String name, String status, double progress, String downSpeed, String upSpeed, 
                      String downloaded, String uploaded, String size, String eta, String peers) {
        this.name = new SimpleStringProperty(name);
        this.status = new SimpleStringProperty(status);
        this.progress = new SimpleDoubleProperty(progress);
        this.downSpeed = new SimpleStringProperty(downSpeed);
        this.upSpeed = new SimpleStringProperty(upSpeed);
        this.downloaded = new SimpleStringProperty(downloaded);
        this.uploaded = new SimpleStringProperty(uploaded);
        this.size = new SimpleStringProperty(size);
        this.eta = new SimpleStringProperty(eta);
        this.peers = new SimpleStringProperty(peers);
    }

    public StringProperty nameProperty() { return name; }
    public StringProperty statusProperty() { return status; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty downSpeedProperty() { return downSpeed; }
    public StringProperty upSpeedProperty() { return upSpeed; }
    public StringProperty downloadedProperty() { return downloaded; }
    public StringProperty uploadedProperty() { return uploaded; }
    public StringProperty sizeProperty() { return size; }
    public StringProperty etaProperty() { return eta; }
    public StringProperty peersProperty() { return peers; }
}
