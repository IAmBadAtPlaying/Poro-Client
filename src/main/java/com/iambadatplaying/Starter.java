package com.iambadatplaying;

import com.google.gson.*;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.frontendHandler.*;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.SocketClient;
import com.iambadatplaying.ressourceServer.ResourceServer;
import com.iambadatplaying.tasks.TaskManager;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Starter {

    public static Starter instance = null;

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

    public static final int DEBUG_FRONTEND_PORT = 3000;
    public static final int DEBUG_FRONTEND_PORT_V2 = 3001;
    public static final int RESSOURCE_SERVER_PORT = 35199;
    public static final int FRONTEND_SOCKET_PORT = 8887;

    private Path taskDirPath = null;


    private Path basePath = null;

    private SocketClient client;
    private SocketServer server;
    private ConnectionManager connectionManager;
    private FrontendMessageHandler frontendMessageHandler;

    private ResourceServer resourceServer;
    private TaskManager taskManager;

    private ConfigLoader configLoader;

    private ConnectionStatemachine connectionStatemachine;

    private ReworkedDataManager reworkedDataManager;

    public static Starter getInstance() {
        if (instance == null) {
            instance = new Starter();
        }
        return instance;
    }

    public static void main(String[] args) {
        Starter starter = Starter.getInstance();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            starter.getConfigLoader().saveConfig();
        }));
        starter.connectionStatemachine = new ConnectionStatemachine(starter);
        starter.run();
    }

    public enum LOG_LEVEL {
        LCU_MESSAGING,
        DEBUG,
        INFO,
        WARN,
        ERROR;
    }

    public void run() {
        if (isShutdownPending()) return;
        initReferences();
        configLoader.loadConfig();
        resourceServer.init();
        connectionManager.init();
        server.init();
        connectionStatemachine.transition(ConnectionStatemachine.State.AWAITING_LEAGUE_PROCESS);
    }

    public void leagueProcessReady() {
        client.init();
        reworkedDataManager.init();
        taskManager.init();
        server.getSockets().forEach(
                socket -> frontendMessageHandler.sendInitialData(socket)
        );
        subscribeToEndpointsOnConnection();
    }

    private void initReferences() {
        configLoader = new ConfigLoader(this);
        resourceServer = new ResourceServer(this);
        connectionManager = new ConnectionManager(this);
        reworkedDataManager = new ReworkedDataManager(this);
        client = new SocketClient(this);
        server = new SocketServer(this);
        frontendMessageHandler = new FrontendMessageHandler(this);
        taskManager = new TaskManager(this);
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

    public void backendMessageReceived(String message) {
        if (message != null && !message.isEmpty()) {
            if (connectionStatemachine.getCurrentState() != ConnectionStatemachine.State.CONNECTED) return;
            JsonElement messageElement = JsonParser.parseString(message);
            JsonArray messageArray = messageElement.getAsJsonArray();
            if (messageArray.isEmpty()) return;
            JsonObject dataPackage = messageArray.get(2).getAsJsonObject();
            new Thread(() -> getReworkedDataManager().update(dataPackage)).start();
            new Thread(() -> getTaskManager().updateAllTasks(message)).start();
        }
    }

    public void shutdown() {
        configLoader.saveConfig();
        resetAllInternal();
        log("Shutting down");
        System.exit(0);
    }

    public void handleLCUDisconnect() {
        connectionManager.setLeagueAuthDataAvailable(false);
        resourceServer.resetCachedData();
        resetLCUDependentComponents();
    }

    private void resetLCUDependentComponents() {
        client.shutdown();
        reworkedDataManager.shutdown();
        taskManager.shutdown();
    }

    public void resetAllInternal() {
        if (isShutdownPending()) {
            resourceServer.shutdown();
            taskManager.shutdown();
            server.shutdown();
            client.shutdown();
            reworkedDataManager.shutdown();
            connectionManager.shutdown();
            resourceServer = null;
            taskManager = null;
            server = null;
            client = null;
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
            taskDirPath = getConfigLoader().getAppFolderPath().resolve(ConfigLoader.USER_DATA_FOLDER_NAME).resolve(ConfigLoader.TASKS_FOLDER_NAME);
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

    public ReworkedDataManager getReworkedDataManager() {
        return reworkedDataManager;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public ConnectionStatemachine getConnectionStatemachine() {
        return connectionStatemachine;
    }

    public ResourceServer getResourceServer() {
        return resourceServer;
    }

    public boolean isShutdownPending() {
        if (connectionStatemachine == null) return false;
        return connectionStatemachine.getCurrentState() == ConnectionStatemachine.State.STOPPING;
    }

    public boolean isInitialized() {
        if (connectionStatemachine == null) return false;
        return connectionStatemachine.getCurrentState() == ConnectionStatemachine.State.CONNECTED;
    }
}
