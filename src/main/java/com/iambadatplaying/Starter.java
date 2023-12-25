package com.iambadatplaying;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.frontendHanlder.FrontendMessageHandler;
import com.iambadatplaying.frontendHanlder.Socket;
import com.iambadatplaying.frontendHanlder.SocketServer;
import com.iambadatplaying.lcuHandler.BackendMessageHandler;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import com.iambadatplaying.lcuHandler.SocketClient;
import com.iambadatplaying.ressourceServer.ResourceServer;
import com.iambadatplaying.tasks.TaskManager;


public class Starter extends MainInitiator {

    public static int ERROR_INVALID_AUTH = 400;
    public static int ERROR_INSUFFICIENT_PERMISSIONS = 401;

    private STATE state = STATE.UNINITIALIZED;

    private SocketClient client;
    private SocketServer server;
    private ConnectionManager connectionManager;
    private FrontendMessageHandler frontendMessageHandler;

    private BackendMessageHandler backendMessageHandler;
    private ResourceServer resourceServer;
    private TaskManager taskManager;

    private ConfigLoader configLoader;

    private DataManager dataManager;
    private ReworkedDataManager reworkedDataManager;

    private volatile boolean running = false;

    public static void main(String[] args) {
        Starter starter = new Starter();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            starter.getConfigLoader().saveConfig();
        }));
        starter.run();
    }

    public void run() {
        setRunning(true);
        initReferences();
        configLoader.loadConfig();
        resourceServer.init();
        connectionManager.init();
        awaitLCUConnection();
        awaitFrontendProcess();
        if (!validAuthString(connectionManager.getAuthString())) {
            System.exit(ERROR_INVALID_AUTH);
        }
        state = STATE.RUNNING;
        client.init();
        server.init();
        reworkedDataManager.init();
        dataManager.init();
        taskManager.init();
        subscribeToEndpointsOnConnection();
    }

    private void initReferences() {
        state = STATE.STARTING;
        configLoader = new ConfigLoader(this);
        resourceServer = new ResourceServer(this);
        connectionManager = new ConnectionManager(this);
        dataManager = new DataManager(this);
        reworkedDataManager = new ReworkedDataManager(this);
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
        state = STATE.AWAITING_LCU;
        while (!connectionManager.isLeagueAuthDataAvailable() && isRunning()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void awaitFrontendProcess() {
        while (!feProcessesReady() && isRunning()) {
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

    public void frontendMessageReceived(String message, Socket socket) {
        if (message != null && !message.isEmpty()) {
            if (this.state != STATE.RUNNING) return;
            new Thread(() -> getFrontendMessageHandler().handleMessage(message, socket)).start();
        }
    }

    public void backendMessageReceived(String message) {
        if (message != null && !message.isEmpty()) {
            if (getState() != STATE.RUNNING) return;
            JsonElement messageElement = JsonParser.parseString(message);
            JsonArray messageArray = messageElement.getAsJsonArray();
            if (messageArray.isEmpty()) return;
            JsonObject dataPackage = messageArray.get(2).getAsJsonObject();
            new Thread(() -> getReworkedDataManager().update(dataPackage)).start();
            new Thread(() -> getTaskManager().updateAllTasks(message)).start();
        }
    }

    public void shutdown() {
        state = STATE.STOPPING;
        setRunning(false);
        configLoader.saveConfig();
        resetAllInternal();
        state = STATE.STOPPED;
        log("Shutting down");
        System.exit(0);
    }

    public void handleGracefulReset() {
        if (isRunning()) {
            state = STATE.RESTARTING;
            resetAllInternal();
            while (isRunning()) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
            }
            run();
        }
    }

    public void resetAllInternal() {
        if (isRunning()) {
            resourceServer.shutdown();
            taskManager.shutdown();
            server.shutdown();
            client.shutdown();
            dataManager.shutdown();
            reworkedDataManager.shutdown();
            connectionManager.shutdown();
            resourceServer = null;
            taskManager = null;
            server = null;
            client = null;
            dataManager = null;
            connectionManager = null;
            setRunning(false);
        }
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

    public ReworkedDataManager getReworkedDataManager() {
        return reworkedDataManager;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean newStatus) {
        running = newStatus;
    }

    public STATE getState() {
        return state;
    }
}
