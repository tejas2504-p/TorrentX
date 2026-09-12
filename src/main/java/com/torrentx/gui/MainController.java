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
            // For now, load a dummy torrent file to test integration without a full FileChooser dialog
            // We use a dummy file in the temp directory, or just mock it, but we can't fully run Add without a real torrent file.
            // Wait, we need a .torrent file to test AddTorrent workflow correctly. 
            // In a real flow, we'd open a FileChooser here. Let's do that quickly.
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Open Torrent File");
            fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Torrent Files", "*.torrent"));
            java.io.File file = fileChooser.showOpenDialog(torrentTable.getScene().getWindow());
            
            if (file != null) {
                javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
                dirChooser.setTitle("Select Download Directory");
                java.io.File dir = dirChooser.showDialog(torrentTable.getScene().getWindow());
                if (dir != null) {
                    torrentService.addTorrent(file, dir, row -> torrentList.add(row));
                }
            }
        }
    }

    @FXML
    private void handleStart() {
        logger.info("Start clicked");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && torrentService != null) {
            torrentService.startTorrent(selected);
        }
    }

    @FXML
    private void handlePause() {
        logger.info("Pause clicked");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && torrentService != null) {
            torrentService.pauseTorrent(selected);
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
            torrentService.stopTorrent(selected);
        }
    }

    @FXML
    private void handleRemove() {
        logger.info("Remove clicked");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null && torrentService != null) {
            torrentService.removeTorrent(selected);
            torrentList.remove(selected);
        }
    }
}
