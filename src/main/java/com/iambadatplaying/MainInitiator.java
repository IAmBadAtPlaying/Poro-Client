package com.iambadatplaying;

import com.iambadatplaying.frontendHanlder.FrontendMessageHandler;
import com.iambadatplaying.frontendHanlder.SocketServer;
import com.iambadatplaying.ressourceServer.ResourceServer;
import com.iambadatplaying.lcuHandler.*;
import com.iambadatplaying.tasks.TaskManager;
import org.eclipse.jetty.websocket.api.Session;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainInitiator {

    public enum LOG_LEVEL {
        DEBUG(0),
        INFO(1),
        ERROR(2);

        private int value;

        private LOG_LEVEL(int level) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
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

    private STATE state = STATE.UNINITIALIZED;

    private SocketClient client;
    private SocketServer server;
    private ConnectionManager connectionManager;
    private FrontendMessageHandler frontendMessageHandler;

    private BackendMessageHandler backendMessageHandler;
    private ResourceServer resourceServer;
    private TaskManager taskManager;

    private DataManager dataManager;

    private Path taskDirPath;


    private String basePath = null;

    private volatile boolean running = false;

    public static String[] requiredEndpoints = {"OnJsonApiEvent_lol-gameflow_v1_gameflow-phase", "OnJsonApiEvent_lol-lobby_v2_lobby", "OnJsonApiEvent_lol-champ-select_v1_session", "OnJsonApiEvent_lol-chat_v1_friends", "OnJsonApiEvent_lol-regalia_v2_summoners","OnJsonApiEvent_lol-loot_v2_player-loot-map","OnJsonApiEvent_lol-loot_v1_ready","OnJsonApiEvent_lol-player-preferences_v1_player-preferences-ready","OnJsonApiEvent_lol-loot_v1_player-loot"};

    public static void main(String[] args) {
        MainInitiator mainInit = new MainInitiator();
        mainInit.init();
        mainInit.setRunning(true);
    }

    public Path getTaskPath() {
        if (taskDirPath == null) {
            try {
                URL location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
                Path currentDirPath = Paths.get(location.toURI()).getParent();
                log("Location: " + currentDirPath, MainInitiator.LOG_LEVEL.INFO);
                Path taskDir = Paths.get(currentDirPath.toString() + "/tasks");
                if (!Files.exists(taskDir)) {
                    taskDir.toFile().mkdir();
                    log("Created tasks directory " + taskDir);
                }
                taskDirPath = taskDir;
                return taskDir;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
        return taskDirPath;
    }

    public void init() {
        state = STATE.STARTING;
        if (isRunning()) {
            return;
        }
        log("Initializing");
        try {
            //TODO: Change this ugly shit holy fucking god
            resourceServer = new ResourceServer(this);
            resourceServer.init();
            connectionManager = new ConnectionManager(this);
            dataManager = new DataManager(this);
            client = new SocketClient(this);
            server = new SocketServer(this);
            backendMessageHandler = new BackendMessageHandler(this);
            frontendMessageHandler = new FrontendMessageHandler(this);
            taskManager = new TaskManager(this);
            connectionManager.init();
            state = STATE.AWAITING_LCU;
            while (!connectionManager.isLeagueAuthDataAvailable()) {
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                }
            }
            while (!feProcessesReady()) {
                try {
                    Thread.sleep(500); //League Backend Socket needs time to be able to serve the resources TODO: This is fucking horrible
                } catch (Exception e) {

                }
            }
            if (connectionManager.authString != null) {
                client.init();
                server.init();
                dataManager.init();
                taskManager.init();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
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
                    }
                }).start();
                setRunning(true);
                state = STATE.RUNNING;
            } else {
                System.out.println("Error, League is not running");
                System.exit(1);
            }
            while (client.getSocket() == null || !client.getSocket().isConnected()) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(1000);
                showRunningNotification();
                Desktop.getDesktop().browse(new URI("http://localhost:3000"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private boolean feProcessesReady() {
        try {
            String resp = (String) connectionManager.getResponse(ConnectionManager.responseFormat.STRING, connectionManager.buildConnection(ConnectionManager.conOptions.GET,"/lol-player-preferences/v1/player-preferences-ready"));
            boolean isReady = "true".equals(resp.trim());
            log("Loot is ready: " + isReady);
            return isReady;
        } catch (Exception e) {

        }
        return false;
    }

    public void shutdown() {
        state = STATE.STOPPING;
        setRunning(false);
        resetAllInternal();
        state = STATE.STOPPED;
        System.exit(0);
    }

    public void resetAllInternal() {
        if (isRunning()) {
            resourceServer.shutdown();
            taskManager.shutdown();
            server.shutdown();
            client.shutdown();
            dataManager.shutdown();
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

    public void handleGracefulReset() {
        if (isRunning()) {
            state = STATE.RESTARTING;
            resetAllInternal();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {

            }
            init();
        }

    }

    private void showRunningNotification() {
        try {
            String body = "{\"data\": {\"title\": \"Poro Client connected!\", \"details\": \"http://127.0.0.1:35199/static/index.html\" }, \"critical\": false, \"detailKey\": \"pre_translated_details\",\"backgroundUrl\" : \"https://cdn.discordapp.com/attachments/313713209314115584/1067507653028364418/Test_2.01.png\",\"iconUrl\": \"https://raw.communitydragon.org/latest/plugins/rcp-fe-lol-settings/global/default/poro_smile.png\", \"titleKey\": \"pre_translated_title\"}";
            HttpURLConnection con = getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/player-notifications/v1/notifications", body);
            con.getResponseCode();
            con.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void log(String s, LOG_LEVEL level) {
        if (level.ordinal() < 0) {
            return;
        }
        String prefix = "[" + level.name() + "]";
        switch (level) {
            case ERROR:
                prefix = "\u001B[31m" + prefix + "\u001B[0m";
                break;
            case DEBUG:
                prefix = "\u001B[32m" + prefix + "\u001B[0m";
                break;
            case INFO:
                prefix = "\u001B[33m" + prefix + "\u001B[0m";
                break;
        }
        System.out.println(prefix + ": " + s);
    }

    public static void log(String s) {
        log(s, LOG_LEVEL.DEBUG);
    }

    public void frontendMessageReceived(String message, Session session) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                frontendMessageHandler.handleMessage(message, session);
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

    public String getBasePath() {
        return basePath;
    }

    public FrontendMessageHandler getFrontendMessageHandler() {
        return frontendMessageHandler;
    }

    public BackendMessageHandler getBackendMessageHandler() {
        return backendMessageHandler;
    }

    public boolean isRunning() {
        return running;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public void setRunning(boolean newStatus) {
        running = newStatus;
    }

    public STATE getState() {
        return state;
    }

}
