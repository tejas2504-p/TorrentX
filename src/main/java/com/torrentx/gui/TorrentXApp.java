package com.torrentx.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TorrentXApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 950, 600);
        scene.getStylesheets().add(getClass().getResource("/ui/main.css").toExternalForm());
        
        primaryStage.setTitle("TorrentX - Premium BitTorrent Client");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
