package com.torrentx.gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Encapsulates the main user interface window container, Stage initialization, and stylesheets.
 */
public class MainWindow {

    private final Stage stage;

    /**
     * Constructs a MainWindow wrapper.
     */
    public MainWindow(Stage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("Stage cannot be null");
        }
        this.stage = stage;
    }

    /**
     * Loads the FXML layout, applies css stylesheets, and displays the main window stage.
     *
     * @throws Exception if FXML or stylesheet loading fails.
     */
    public void show() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 950, 600);
        scene.getStylesheets().add(getClass().getResource("/ui/main.css").toExternalForm());
        
        stage.setTitle("TorrentX - Premium BitTorrent Client");
        stage.setScene(scene);
        stage.show();
    }

    public Stage getStage() {
        return stage;
    }
}
