package com.torrentx.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // Add a placeholder dummy torrent to verify UI bindings and layout
        torrentList.add(new TorrentRow(
                "TorrentX_Project_Foundation_Demo.zip",
                "128.5 MB",
                0.42,
                "Downloading",
                "1.2 MB/s",
                "45.2 KB/s",
                "12"
        ));
        
        logger.info("MainController initialized successfully with dummy data.");
    }

    @FXML
    private void handleAddTorrent() {
        logger.info("Add Torrent button clicked (stub action).");
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Add Torrent feature will be implemented in a future phase.", ButtonType.OK);
        alert.showAndWait();
    }

    @FXML
    private void handlePause() {
        logger.info("Pause button clicked (stub action).");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selected.statusProperty().set("Paused");
            selected.downSpeedProperty().set("0 KB/s");
            selected.upSpeedProperty().set("0 KB/s");
            selected.peersProperty().set("0");
            logger.info("Paused torrent: {}", selected.nameProperty().get());
        }
    }

    @FXML
    private void handleResume() {
        logger.info("Resume button clicked (stub action).");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selected.statusProperty().set("Downloading");
            selected.downSpeedProperty().set("1.5 MB/s");
            selected.upSpeedProperty().set("50.0 KB/s");
            selected.peersProperty().set("15");
            logger.info("Resumed torrent: {}", selected.nameProperty().get());
        }
    }

    @FXML
    private void handleRemove() {
        logger.info("Remove button clicked (stub action).");
        TorrentRow selected = torrentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            torrentList.remove(selected);
            logger.info("Removed torrent: {}", selected.nameProperty().get());
        }
    }
}
