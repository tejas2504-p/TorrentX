package com.torrentx.gui;

import com.torrentx.core.ClientManager;
import com.torrentx.gui.service.TorrentService;
import javafx.application.Application;
import javafx.stage.Stage;

public class TorrentXApp extends Application {
    
    private ClientManager clientManager;
    private TorrentService torrentService;

    @Override
    public void init() throws Exception {
        super.init();
        clientManager = new ClientManager();
        torrentService = new TorrentService(clientManager);
        torrentService.startBackend();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        MainWindow mainWindow = new MainWindow(primaryStage, torrentService);
        mainWindow.show();
    }
    
    @Override
    public void stop() throws Exception {
        if (torrentService != null) {
            torrentService.stopBackend();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
