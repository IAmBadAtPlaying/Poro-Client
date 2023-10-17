package com.iambadatplaying;

import com.iambadatplaying.frontendHanlder.FrontendMessageHandler;
import com.iambadatplaying.frontendHanlder.SocketServer;
import com.iambadatplaying.lcuHandler.BackendMessageHandler;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import com.iambadatplaying.lcuHandler.SocketClient;
import com.iambadatplaying.ressourceServer.ResourceServer;
import com.iambadatplaying.tasks.TaskManager;
import org.eclipse.jetty.websocket.api.Session;

import java.awt.Desktop;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class Starter extends MainInitiator {

    public static int ERROR_INVALID_AUTH = 400;
    public static int ERROR_INSUFFICIENT_PERMISSIONS = 401;

    private SocketClient client;
    private SocketServer server;
    private ConnectionManager connectionManager;
    private FrontendMessageHandler frontendMessageHandler;

    private BackendMessageHandler backendMessageHandler;
    private ResourceServer resourceServer;
    private TaskManager taskManager;

    private DataManager dataManager;

    public static void main(String[] args) {
        Starter starter = new Starter();
        starter.run();
    }

    public void run() {
        initReferences();
        resourceServer.init();
        connectionManager.init();
        awaitLCUConnection();
        awaitFrontendProcess();
        if (!validAuthString(connectionManager.getAuthString())) {
            System.exit(ERROR_INVALID_AUTH);
        }
        client.init();
        server.init();
        dataManager.init();
        taskManager.init();
        subscribeToEndpointsOnConnection();
    }

    private void initReferences() {
        resourceServer = new ResourceServer(this);
        connectionManager = new ConnectionManager(this);
        dataManager = new DataManager(this);
        client = new SocketClient(this);
        server = new SocketServer(this);
        backendMessageHandler = new BackendMessageHandler(this);
        frontendMessageHandler = new FrontendMessageHandler(this);
        taskManager = new TaskManager(this);
    }

    private boolean validAuthString(String authString) {
        return authString != null;
    }

    private void awaitLCUConnection() {
        while (!connectionManager.isLeagueAuthDataAvailable()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void awaitFrontendProcess() {
        while (!feProcessesReady()) {
            try {
                log("Waiting");
                Thread.sleep(500); //League Backend Socket needs time to be able to serve the resources TODO: This is fucking horrible
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private boolean feProcessesReady() {
        try {
            String resp = (String) connectionManager.getResponse(ConnectionManager.responseFormat.STRING, connectionManager.buildConnection(ConnectionManager.conOptions.GET,"/lol-player-preferences/v1/player-preferences-ready"));
            boolean isReady = "true".equals(resp.trim());
            log("FE-Process ready: " + isReady);
            return isReady;
        } catch (Exception e) {

        }
        return false;
    }

    private void subscribeToEndpointsOnConnection() {
        new Thread(() -> {
            while (getClient().getSocket() == null || !getClient().getSocket().isConnected()) {
                try {
                    Thread.sleep(10);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            for (String endpoint : requiredEndpoints) {
                getClient().getSocket().subscribeToEndpoint(endpoint);
            }
        }).start();
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public SocketClient getClient() {
        return client;
    }

    public SocketServer getServer() {
        return server;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public FrontendMessageHandler getFrontendMessageHandler() {
        return frontendMessageHandler;
    }

    public BackendMessageHandler getBackendMessageHandler() {
        return backendMessageHandler;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
