package com.iambadatplaying;

import com.google.gson.*;
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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Starter {

    public static final boolean isDev = true;

    public static int ERROR_INVALID_AUTH = 400;
    public static int ERROR_INSUFFICIENT_PERMISSIONS = 401;
    public static int ERROR_CERTIFICATE_SETUP_FAILED = 495;

    public static int ERROR_HTTP_PATCH_SETUP = 505;

    public static final int VERSION_MAJOR = 0;
    public static final int VERSION_MINOR = 1;
    public static final int VERSION_PATCH = 4;

    public static String[] requiredEndpoints = {"OnJsonApiEvent"};

    private static final String appDirName = "poroclient";

    public static final int RESSOURCE_SERVER_PORT = 35199;
    public static final int FRONTEND_SERVER_PORT = 8887;

    private volatile STATE state = STATE.UNINITIALIZED;

    private Path taskDirPath = null;


    private Path basePath = null;

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


    public static void main(String[] args) {
        Starter starter = new Starter();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            starter.getConfigLoader().saveConfig();
        }));
        starter.run();
    }

    public enum LOG_LEVEL {
        LCU_MESSAGING,
        DEBUG,
        INFO,
        WARN,
        ERROR;
    }

    public enum STATE {
        UNINITIALIZED,
        STARTING,
        RESTARTING,
        AWAITING_LCU,
        RUNNING,
        STOPPING,
        STOPPED;

        public STATE getStateFromString(String s) {
            for (STATE state : STATE.values()) {
                if (state.name().equalsIgnoreCase(s)) {
                    return state;
                }
            }
            return null;
        }
    }

    public void run() {
        if (isShutdownPending()) return;
        state = STATE.STARTING;
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
        while (!connectionManager.isLeagueAuthDataAvailable() && !isShutdownPending()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void awaitFrontendProcess() {
        while (!feProcessesReady() && !isShutdownPending()) {
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
        configLoader.saveConfig();
        resetAllInternal();
        state = STATE.STOPPED;
        log("Shutting down");
        System.exit(0);
    }

    public void handleGracefulReset() {
        if (isInitialized()) {
            state = STATE.RESTARTING;
            resetAllInternal();
            run();
        }
    }

    public void resetAllInternal() {
        if (state == STATE.RESTARTING || isShutdownPending()) {
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
        }
    }

    public void log(String s, LOG_LEVEL level) {
        if (isDev) {
            switch (level) {
                case ERROR:
                case INFO:
                case DEBUG:
                case LCU_MESSAGING:
                    break;
                default:
                    return;
            }
        }
        String prefix = "[" + level.name() + "]";
        switch (level) {
            case ERROR:
                prefix = "\u001B[31m" + prefix + "\u001B[0m";
                break;
            case DEBUG:
                prefix = "\u001B[32m" + prefix + "\u001B[0m";
                break;
            case LCU_MESSAGING:
                prefix = "\u001B[34m" + prefix + "\u001B[0m";
                break;
            case INFO:
                prefix = "\u001B[33m" + prefix + "\u001B[0m";
                break;
        }
        System.out.println(prefix + ": " + s);
    }

    public void log(String s) {
        log(s, LOG_LEVEL.DEBUG);
    }

    public static String getAppDirName() {
        return appDirName;
    }

    public Path getTaskPath() {
        if (taskDirPath == null) {
            try {
                URL location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                Path currentDirPath = Paths.get(location.toURI()).getParent();
                log("Location: " + currentDirPath, LOG_LEVEL.INFO);
                Path taskDir = Paths.get(currentDirPath.toString() + "/tasks");
                if (!Files.exists(taskDir)) {
                    if (taskDir.toFile().mkdir()) {
                        log("Created tasks directory " + taskDir);
                        taskDirPath = taskDir;
                    } else {
                        log("Failed to create tasks directory " + taskDir, LOG_LEVEL.ERROR);
                        taskDirPath = null;
                    }
                } else {
                    taskDirPath = taskDir;
                }
                return taskDir;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
        return taskDirPath;
    }

    public Path getBasePath() {
        if (basePath == null) {
            try {
                URL location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                Path currentDirPath = Paths.get(location.toURI()).getParent();
                log("Base-Location: " + currentDirPath, LOG_LEVEL.INFO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return basePath;
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

    public boolean isShutdownPending() {
        return state == STATE.STOPPING || state == STATE.STOPPED;
    }

    public boolean isInitialized() {
        return state == STATE.RUNNING;
    }


    public STATE getState() {
        return state;
    }

}
