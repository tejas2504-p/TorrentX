package com.torrentx.ui;

import com.torrentx.bencode.BencodeDecoder;
import com.torrentx.bencode.Metainfo;
import com.torrentx.core.PieceSelectionStrategy;
import com.torrentx.core.PieceSelector;
import com.torrentx.core.TorrentEngine;
import com.torrentx.peer.PeerManager;
import com.torrentx.peer.PeerService;
import com.torrentx.storage.StorageManager;
import com.torrentx.storage.StorageService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private TableView<TorrentRow> torrentTable;
    @FXML
    private TableColumn<TorrentRow, String> nameColumn;
    @FXML
    private TableColumn<TorrentRow, String> sizeColumn;
    @FXML
    private TableColumn<TorrentRow, Double> progressColumn;
    @FXML
    private TableColumn<TorrentRow, String> statusColumn;
    @FXML
    private TableColumn<TorrentRow, String> downSpeedColumn;
    @FXML
    private TableColumn<TorrentRow, String> upSpeedColumn;
    @FXML
    private TableColumn<TorrentRow, String> peersColumn;
    @FXML
    private Label globalSpeedLabel;

    private final ObservableList<TorrentRow> torrentList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        sizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        progressColumn.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        downSpeedColumn.setCellValueFactory(cellData -> cellData.getValue().downSpeedProperty());
        upSpeedColumn.setCellValueFactory(cellData -> cellData.getValue().upSpeedProperty());
        peersColumn.setCellValueFactory(cellData -> cellData.getValue().peersProperty());

        torrentTable.setItems(torrentList);
    }

    @FXML
    @SuppressWarnings("unchecked")
    private void handleAddTorrent() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Torrent File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Torrent Files (*.torrent)", "*.torrent"));
        File selectedFile = fileChooser.showOpenDialog(torrentTable.getScene().getWindow());

        if (selectedFile == null) return;

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Download Directory");
        File downloadDir = dirChooser.showDialog(torrentTable.getScene().getWindow());

        if (downloadDir == null) return;

        try {
            byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
            BencodeDecoder decoder = new BencodeDecoder(fileBytes);
            Map<String, Object> torrentMap = (Map<String, Object>) decoder.decode();
            Metainfo metainfo = new Metainfo(torrentMap, decoder.getRawInfoBytes());

            TorrentRow row = new TorrentRow(
                    metainfo.getName(),
                    formatSize(metainfo.getTotalLength()),
                    0.0,
                    "Checking",
                    "0 KB/s",
                    "0 KB/s",
                    "0",
                    null
            );

            int localPort = 6881;
            byte[] localPeerId = TorrentEngine.generatePeerId();
            StorageService storageManager = new StorageManager(downloadDir, metainfo);
            PeerService peerManager = new PeerManager(metainfo, localPeerId, localPort);
            PieceSelectionStrategy pieceSelector = new PieceSelector(metainfo.getPieceCount());

            TorrentEngine engine = new TorrentEngine(metainfo, downloadDir, localPort,
                    storageManager, peerManager, pieceSelector, new TorrentEngine.TorrentStateListener() {
                @Override
                public void onProgressUpdate(double progress, double downloadSpeed, double uploadSpeed, int activePeers) {
                    Platform.runLater(() -> {
                        row.progressProperty().set(progress);
                        row.statusProperty().set(progress >= 1.0 ? "Seeding" : "Downloading");
                        row.peersProperty().set(String.valueOf(activePeers));
                        row.downSpeedProperty().set(String.format("%.1f KB/s", downloadSpeed));
                        row.upSpeedProperty().set(String.format("%.1f KB/s", uploadSpeed));
                    });
                }

                @Override
                public void onTorrentComplete() {
                    Platform.runLater(() -> {
                        row.progressProperty().set(1.0);
                        row.statusProperty().set("Finished");
                        row.downSpeedProperty().set("0 KB/s");
                    });
                }

                @Override
                public void onError(String message) {
                    Platform.runLater(() -> {
                        row.statusProperty().set("Error");
                        logger.error("Engine error reported: {}", message);
                    });
                }
            });

            peerManager.setListener(engine);

            TorrentRow activeRow = new TorrentRow(
                    metainfo.getName(),
                    formatSize(metainfo.getTotalLength()),
                    0.0,
                    "Starting",
                    "0 KB/s",
                    "0 KB/s",
                    "0",
                    engine
            );

            torrentList.add(activeRow);
            engine.start();

        } catch (Exception e) {
            logger.error("Failed to add and start torrent task", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to parse or add torrent:\n" + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void handlePause() {
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getEngine() != null) {
            selected.getEngine().stop();
            selected.statusProperty().set("Paused");
            selected.downSpeedProperty().set("0 KB/s");
            selected.upSpeedProperty().set("0 KB/s");
        }
    }

    @FXML
    private void handleResume() {
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getEngine() != null) {
            try {
                selected.getEngine().start();
                selected.statusProperty().set("Resuming");
            } catch (IOException e) {
                logger.error("Failed to resume torrent task", e);
            }
        }
    }

    @FXML
    private void handleRemove() {
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (selected.getEngine() != null) {
                selected.getEngine().stop();
            }
            torrentList.remove(selected);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }
}
