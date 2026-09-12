package com.torrentx.gui;

import com.torrentx.gui.service.TorrentService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private TableView<TorrentRow> torrentTable;
    @FXML private TableColumn<TorrentRow, String> nameColumn;
    @FXML private TableColumn<TorrentRow, String> statusColumn;
    @FXML private TableColumn<TorrentRow, Double> progressColumn;
    @FXML private TableColumn<TorrentRow, String> downSpeedColumn;
    @FXML private TableColumn<TorrentRow, String> upSpeedColumn;
    @FXML private TableColumn<TorrentRow, String> downloadedColumn;
    @FXML private TableColumn<TorrentRow, String> uploadedColumn;
    @FXML private TableColumn<TorrentRow, String> sizeColumn;
    @FXML private TableColumn<TorrentRow, String> etaColumn;
    @FXML private TableColumn<TorrentRow, String> peersColumn;
    
    @FXML private Label globalSpeedLabel;

    private final ObservableList<TorrentRow> torrentList = FXCollections.observableArrayList();
    private TorrentService torrentService;

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        progressColumn.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        downSpeedColumn.setCellValueFactory(cellData -> cellData.getValue().downSpeedProperty());
        upSpeedColumn.setCellValueFactory(cellData -> cellData.getValue().upSpeedProperty());
        downloadedColumn.setCellValueFactory(cellData -> cellData.getValue().downloadedProperty());
        uploadedColumn.setCellValueFactory(cellData -> cellData.getValue().uploadedProperty());
        sizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        etaColumn.setCellValueFactory(cellData -> cellData.getValue().etaProperty());
        peersColumn.setCellValueFactory(cellData -> cellData.getValue().peersProperty());

        torrentTable.setItems(torrentList);
        torrentTable.setPlaceholder(new Label("No torrents in the list. Add a torrent to start."));

        logger.info("MainController initialized successfully.");
    }

    public void setTorrentService(TorrentService torrentService) {
        this.torrentService = torrentService;
        // In a real application, we would subscribe to the service for updates here
    }

    @FXML
    private void handleAddTorrent() {
        logger.info("Add Torrent clicked");
        if (torrentService != null) {
            torrentService.executeTask(() -> {
                logger.info("Executing add torrent logic in backend");
                // Mock adding a torrent for UI testing purposes
                Platform.runLater(() -> {
                    torrentList.add(new TorrentRow(
                        "ubuntu-24.04-desktop-amd64.iso", "Downloading", 0.05, 
                        "2.5 MB/s", "100 KB/s", "150 MB", "5 MB", "3.0 GB", "15m", "25/40"
                    ));
                });
            }, "Add Torrent");
        }
    }

    @FXML
    private void handleStart() {
        logger.info("Start clicked");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && torrentService != null) {
            torrentService.executeTask(() -> {
                logger.info("Starting torrent: {}", selected.nameProperty().get());
                Platform.runLater(() -> selected.statusProperty().set("Downloading"));
            }, "Start Torrent");
        }
    }

    @FXML
    private void handlePause() {
        logger.info("Pause clicked");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && torrentService != null) {
            torrentService.executeTask(() -> {
                logger.info("Pausing torrent: {}", selected.nameProperty().get());
                Platform.runLater(() -> selected.statusProperty().set("Paused"));
            }, "Pause Torrent");
        }
    }

    @FXML
    private void handleResume() {
        logger.info("Resume clicked");
        handleStart(); // For now, Resume is the same as Start
    }

    @FXML
    private void handleStop() {
        logger.info("Stop clicked");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && torrentService != null) {
            torrentService.executeTask(() -> {
                logger.info("Stopping torrent: {}", selected.nameProperty().get());
                Platform.runLater(() -> selected.statusProperty().set("Stopped"));
            }, "Stop Torrent");
        }
    }

    @FXML
    private void handleRemove() {
        logger.info("Remove clicked");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && torrentService != null) {
            torrentService.executeTask(() -> {
                logger.info("Removing torrent: {}", selected.nameProperty().get());
                Platform.runLater(() -> torrentList.remove(selected));
            }, "Remove Torrent");
        }
    }
}
