package com.torrentx.gui;

import com.torrentx.gui.service.TorrentService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private TableView<TorrentRow> torrentTable;
    @FXML private TableColumn<TorrentRow, Integer> idColumn;
    @FXML private TableColumn<TorrentRow, String> nameColumn;
    @FXML private TableColumn<TorrentRow, String> statusColumn;
    @FXML private TableColumn<TorrentRow, Double> progressColumn;
    @FXML private TableColumn<TorrentRow, String> downSpeedColumn;
    @FXML private TableColumn<TorrentRow, String> upSpeedColumn;
    @FXML private TableColumn<TorrentRow, String> downloadedColumn;
    @FXML private TableColumn<TorrentRow, String> uploadedColumn;
    @FXML private TableColumn<TorrentRow, String> sizeColumn;
    @FXML private TableColumn<TorrentRow, String> etaColumn;
    @FXML private TableColumn<TorrentRow, String> ratioColumn;
    @FXML private TableColumn<TorrentRow, String> addedColumn;
    
    // Stats Dashboard
    @FXML private Text statDownSpeed, statUpSpeed, statActive, statTotalDown, statTotalUp;
    
    // Details Pane
    @FXML private Text detailName, detailPath, detailProgressText, detailStatus, detailEta, detailSize;
    @FXML private Text detailDown, detailUp, detailSeeds, detailPeers, detailRatio, detailAvail, detailAdded;
    @FXML private ProgressBar detailProgress;
    
    // Footer / Chart
    @FXML private Text footerDown, footerUp;
    @FXML private AreaChart<Number, Number> speedChart;

    private final ObservableList<TorrentRow> torrentList = FXCollections.observableArrayList();
    private TorrentService torrentService;

    @FXML
    public void initialize() {
        // Id Column (Row Index)
        idColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(torrentTable.getItems().indexOf(cellData.getValue()) + 1));
        
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    getStyleClass().add("cell-name");
                }
            }
        });

        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("cell-status-downloading", "cell-status-completed", "cell-status-queued", "cell-status-paused", "cell-status-seeding", "cell-status-stopped");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(5);
                    box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    Region icon = new Region();
                    icon.setMinSize(12, 12);
                    icon.setMaxSize(12, 12);
                    
                    if (item.equalsIgnoreCase("Downloading")) {
                        getStyleClass().add("cell-status-downloading");
                        icon.setStyle("-fx-background-color: #3b82f6; -fx-shape: 'M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z';");
                    } else if (item.equalsIgnoreCase("Seeding")) {
                        getStyleClass().add("cell-status-seeding");
                        icon.setStyle("-fx-background-color: #10b981; -fx-shape: 'M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z';");
                    } else if (item.equalsIgnoreCase("Completed")) {
                        getStyleClass().add("cell-status-completed");
                        icon.setStyle("-fx-background-color: #10b981; -fx-shape: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z';");
                    } else if (item.equalsIgnoreCase("Paused")) {
                        getStyleClass().add("cell-status-paused");
                        icon.setStyle("-fx-background-color: #f59e0b; -fx-shape: 'M14,19H18V5H14M6,19H10V5H6V19Z';");
                    } else if (item.equalsIgnoreCase("Stopped")) {
                        getStyleClass().add("cell-status-stopped");
                        icon.setStyle("-fx-background-color: #ef4444; -fx-shape: 'M6,6H18V18H6V6Z';");
                    } else {
                        getStyleClass().add("cell-status-queued");
                        icon.setStyle("-fx-background-color: #6b7280; -fx-shape: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56z';");
                    }
                    
                    Text text = new Text(item);
                    text.getStyleClass().add("cell-status-" + item.toLowerCase());
                    box.getChildren().addAll(icon, text);
                    setGraphic(box);
                }
            }
        });

        progressColumn.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        progressColumn.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            private final Text text = new Text();
            private final HBox box = new HBox(8);
            {
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                bar.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(bar, javafx.scene.layout.Priority.ALWAYS);
                box.getChildren().addAll(bar, text);
            }
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    bar.setProgress(item);
                    text.setText(String.format("%.1f%%", item * 100));
                    text.setStyle("-fx-fill: #6b7280; -fx-font-size: 12px;");
                    bar.getStyleClass().removeAll("table-progress-bar", "table-progress-bar-completed");
                    bar.getStyleClass().add("table-progress-bar");
                    if (item >= 1.0) {
                        bar.getStyleClass().add("table-progress-bar-completed");
                    }
                    setGraphic(box);
                }
            }
        });

        downSpeedColumn.setCellValueFactory(cellData -> cellData.getValue().downSpeedProperty());
        upSpeedColumn.setCellValueFactory(cellData -> cellData.getValue().upSpeedProperty());
        downloadedColumn.setCellValueFactory(cellData -> cellData.getValue().downloadedProperty());
        uploadedColumn.setCellValueFactory(cellData -> cellData.getValue().uploadedProperty());
        sizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        etaColumn.setCellValueFactory(cellData -> cellData.getValue().etaProperty());
        ratioColumn.setCellValueFactory(cellData -> cellData.getValue().ratioProperty());
        addedColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>("Today")); // Mock for now

        torrentTable.setItems(torrentList);
        torrentTable.setPlaceholder(new Label("No torrents in the list. Add a torrent to start."));

        // Selection listener to update details pane
        torrentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                updateDetailsPane(newSel);
            }
        });
        
        setupSpeedChart();

        logger.info("MainController initialized successfully.");
    }
    
    private void setupSpeedChart() {
        XYChart.Series<Number, Number> downSeries = new XYChart.Series<>();
        XYChart.Series<Number, Number> upSeries = new XYChart.Series<>();
        // Mock data
        for (int i = 0; i < 20; i++) {
            downSeries.getData().add(new XYChart.Data<>(i, Math.random() * 15 + 5));
            upSeries.getData().add(new XYChart.Data<>(i, Math.random() * 5));
        }
        speedChart.getData().addAll(downSeries, upSeries);
    }

    private void updateDetailsPane(TorrentRow row) {
        detailName.setText(row.getName());
        detailStatus.setText(row.getStatus());
        detailSize.setText(row.getSize());
        detailDown.setText(row.getDownloaded());
        detailUp.setText(row.getUploaded());
        detailEta.setText(row.getEta());
        detailPeers.setText(row.getPeers());
        detailRatio.setText(row.getRatio());
        
        double prog = row.getProgress();
        detailProgress.setProgress(prog);
        detailProgressText.setText(String.format("%.1f%%", prog * 100));
    }

    public void setTorrentService(TorrentService torrentService) {
        this.torrentService = torrentService;
    }

    @FXML
    private void handleAddTorrent() {
        logger.info("Add Torrent clicked");
        if (torrentService != null) {
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
