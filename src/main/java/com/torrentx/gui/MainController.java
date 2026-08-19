package com.torrentx.gui;

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

        // TODO: In future phases, load active torrents from client manager
        logger.info("MainController initialized successfully.");
    }

    @FXML
    private void handleAddTorrent() {
        // TODO: Implement torrent adding logic in future phases
        throw new UnsupportedOperationException("Adding torrent is not implemented yet.");
    }

    @FXML
    private void handlePause() {
        // TODO: Implement pause logic in future phases
        throw new UnsupportedOperationException("Pausing torrent is not implemented yet.");
    }

    @FXML
    private void handleResume() {
        // TODO: Implement resume logic in future phases
        throw new UnsupportedOperationException("Resuming torrent is not implemented yet.");
    }

    @FXML
    private void handleRemove() {
        // TODO: Implement remove logic in future phases
        throw new UnsupportedOperationException("Removing torrent is not implemented yet.");
    }
}
