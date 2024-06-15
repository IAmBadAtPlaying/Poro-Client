package com.iambadatplaying;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.frontendHandler.SocketServer;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.ArrayList;
import java.util.Optional;

public class ConnectionStatemachine {

    public static final State[] STOPPING_TRANSITIONS = {};
    private static final String[] statusEndpoints = {
            "/lol-summoner/v1/status"
    };
    private static final String[] readyEndpoints = {
            "/lol-game-settings/v1/ready",
            "/lol-loot/v1/ready",
            "/lol-progression/v1/ready",
            "/lol-settings/v2/ready"
    };
    private static final int MAXIMUM_CONNECTION_ATTEMPTS = 3;
    private static final int MAXIMUM_PROCESS_FIND_ATTEMPTS = 30;
    private static final int PROCESS_FIND_ATTEMPT_DELAY = 1000;
    private static final int MAXIMUM_LCU_CONNECTION_ATTEMPTS = 10;
    private static final int LCU_CONNECTION_ATTEMPT_DELAY = 1000;
    private static final int MAXIMUM_LCU_INIT_ATTEMPTS = 10;
    private static final int LCU_INIT_ATTEMPT_DELAY = 1000;
    private static final State[] STARTING_TRANSITIONS = {
            State.AWAITING_LEAGUE_PROCESS,
            State.STOPPING
    };
    private static final State[] AWAITING_LEAGUE_PROCESS_TRANSITIONS = {
            State.NO_PROCESS_IDLE,
            State.AWAITING_LCU_CONNECTION,
            State.STOPPING
    };
    private static final State[] NO_PROCESS_IDLE_TRANSITIONS = {
            State.AWAITING_LEAGUE_PROCESS,
            State.STOPPING
    };
    private static final State[] AWAIT_LCU_CONNECTION_TRANSITIONS = {
            State.AWAITING_LCU_INIT,
            State.AWAITING_LEAGUE_PROCESS,
            State.DISCONNECTED,
            State.STOPPING
    };
    private static final State[] AWAIT_LCU_INIT_TRANSITIONS = {
            State.CONNECTED,
            State.DISCONNECTED,
            State.STOPPING
    };
    private static final State[] CONNECTED_TRANSITIONS = {
            State.DISCONNECTED,
            State.STOPPING
    };
    private static final State[] DISCONNECTED_TRANSITIONS = {
            State.AWAITING_LEAGUE_PROCESS,
            State.STOPPING
    };
    private int failedConnectionAttempts = 0;
    private State currentState = State.STARTING;
    private Starter starter;
    public ConnectionStatemachine(Starter starter) {
        initTransitions();
        this.starter = starter;
        onStateChange(currentState);
    }

    private ConnectionStatemachine() {
    }

    private void initTransitions() {
        State.STARTING.setTransitions(
                STARTING_TRANSITIONS
        );

        State.AWAITING_LEAGUE_PROCESS.setTransitions(
                AWAITING_LEAGUE_PROCESS_TRANSITIONS
        );

        State.NO_PROCESS_IDLE.setTransitions(
                NO_PROCESS_IDLE_TRANSITIONS
        );

        State.AWAITING_LCU_CONNECTION.setTransitions(
                AWAIT_LCU_CONNECTION_TRANSITIONS
        );

        State.AWAITING_LCU_INIT.setTransitions(
                AWAIT_LCU_INIT_TRANSITIONS
        );

        State.CONNECTED.setTransitions(
                CONNECTED_TRANSITIONS
        );

        State.DISCONNECTED.setTransitions(
                DISCONNECTED_TRANSITIONS
        );

        State.STOPPING.setTransitions(
                STOPPING_TRANSITIONS
        );
    }

    public void transition(State newState) {
        if (currentState == newState || currentState == State.STOPPING) {
            return;
        }

        if (!currentState.isValidTransition(newState)) {
            log("Invalid transition from " + currentState + " to " + newState, Starter.LOG_LEVEL.ERROR);
            return;
        }

        log(
                "Transitioning from " + currentState + " to " + newState,
                Starter.LOG_LEVEL.DEBUG
        );
        this.currentState = newState;
        new Thread(
                () -> onStateChange(newState)
        ).start();
    }

    public State getCurrentState() {
        return currentState;
    }

    private void onStateChange(State newState) {
        if (starter == null) {
            return;
        }
        Optional<SocketServer> optServer = Optional.ofNullable(starter.getServer());
        optServer.ifPresent(server -> {

            JsonObject status = new JsonObject();
            status.addProperty("state", newState.toString());
            server.sendToAllSessions(
                    ReworkedDataManager.getEventDataString(
                            ReworkedDataManager.UPDATE_TYPE_INTERNAL_STATE,
                            status
                    )
            );
        });
        switch (newState) {
            case STARTING:
                return;
            case AWAITING_LEAGUE_PROCESS:
                handleAwatingLeagueProcess();
                return;
            case NO_PROCESS_IDLE:
                handleNoProcessIdle();
                return;
            case AWAITING_LCU_CONNECTION:
                handleAwaitingLcuConnection();
                return;
            case AWAITING_LCU_INIT:
                handleAwaitingLcuInit();
                return;
            case CONNECTED:
                handleConnected();
                return;
            case DISCONNECTED:
                handleDisconnected();
                return;
            case STOPPING:
                handleStopping();
        }
    }

    private void handleAwatingLeagueProcess() {
        ConnectionManager cm = Starter.getInstance().getConnectionManager();

        for (int i = 0; i < MAXIMUM_PROCESS_FIND_ATTEMPTS; i++) {
            if (Starter.getInstance().isShutdownPending()) {
                return;
            }


            if (cm.getAuthFromProcess()) {
                cm.setLeagueAuthDataAvailable(true);
                transition(State.AWAITING_LCU_CONNECTION);
                return;
            }

            try {
                Thread.sleep(PROCESS_FIND_ATTEMPT_DELAY);
            } catch (InterruptedException e) {
                return;
            }
        }

        transition(State.NO_PROCESS_IDLE);
    }

    private void handleNoProcessIdle() {
        log("No process found, will not continue to search for the League Client Process", Starter.LOG_LEVEL.INFO);
    }

    private void handleAwaitingLcuConnection() {
        for (int i = 0; i < MAXIMUM_LCU_CONNECTION_ATTEMPTS; i++) {
            if (Starter.getInstance().isShutdownPending()) {
                return;
            }

            log("Checking general connection");

            if (checkGeneralConnection()) {
                log("General connection established");
                transition(State.AWAITING_LCU_INIT);
                return;
            }

            try {
                Thread.sleep(LCU_CONNECTION_ATTEMPT_DELAY);
            } catch (InterruptedException e) {
                return;
            }
        }

        connectionAttemptFailed();
        transition(State.DISCONNECTED);
    }

    private void handleAwaitingLcuInit() {
        ArrayList<String> checkedEndpoints = new ArrayList<>();

        for (int i = 0; i < MAXIMUM_LCU_INIT_ATTEMPTS; i++) {
            if (Starter.getInstance().isShutdownPending()) {
                return;
            }

            String lastFailedEndpoint = null;

            for (String endpoint : readyEndpoints) {
                log("Checking endpoint: " + endpoint);
                if (endpointReady(endpoint)) {
                    checkedEndpoints.add(endpoint);
                } else {
                    log("Endpoint not ready: " + endpoint);
                    lastFailedEndpoint = endpoint;
                    break;
                }
            }

            if (lastFailedEndpoint == null) {
                for (String endpoint : statusEndpoints) {
                    if (statusReady(endpoint)) {
                        checkedEndpoints.add(endpoint);
                    } else {
                        log("Status-Endpoint not ready: " + endpoint);
                        lastFailedEndpoint = endpoint;
                        break;
                    }
                }
            }

            if (lastFailedEndpoint == null) {
                log("All endpoints ready: " + checkedEndpoints);
                transition(State.CONNECTED);
                return;
            } else {
                log("Not all endpoints ready!");
                log("Last failed endpoint: " + lastFailedEndpoint);
            }

            try {
                Thread.sleep(LCU_INIT_ATTEMPT_DELAY);
            } catch (InterruptedException e) {
                return;
            }
        }

        connectionAttemptFailed();
        transition(State.DISCONNECTED);
    }

    private void handleConnected() {
        resetConnectionAttempts();
        Starter.getInstance().leagueProcessReady();
    }

    private void handleDisconnected() {
        //Disconnect might mean the user changed the account
        if (starter.isShutdownPending()) return;
        starter.handleLCUDisconnect();
        transition(State.AWAITING_LEAGUE_PROCESS);
    }

    private void handleStopping() {
        starter.shutdown();
    }

    private void resetConnectionAttempts() {
        failedConnectionAttempts = 0;
    }

    private void connectionAttemptFailed() {
        failedConnectionAttempts++;
        if (failedConnectionAttempts >= MAXIMUM_CONNECTION_ATTEMPTS) {
            log("Repeated connection attempts failed, stopping", Starter.LOG_LEVEL.ERROR);
            transition(State.STOPPING);
        }
    }

    private boolean checkGeneralConnection() {
        try {
            ConnectionManager cm = Starter.getInstance().getConnectionManager();
            JsonArray respJson = ConnectionManager.getResponseBodyAsJsonArray(cm.buildConnection(ConnectionManager.conOptions.GET, "/riotclient/command-line-args"));
            if (respJson == null) return false;
            return !respJson.isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean statusReady(String endpoint) {
        try {
            ConnectionManager cm = Starter.getInstance().getConnectionManager();
            JsonObject respJson = ConnectionManager.getResponseBodyAsJsonObject(cm.buildConnection(ConnectionManager.conOptions.GET, endpoint));
            if (respJson == null) return false;
            return respJson.get("ready").getAsBoolean();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean endpointReady(String endpoint) {
        try {
            ConnectionManager cm = Starter.getInstance().getConnectionManager();
            String resp = ConnectionManager.handleStringResponse(cm.buildConnection(ConnectionManager.conOptions.GET, endpoint));
            if (resp == null) return false;
            return "true".equals(resp.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void log(String s) {
        log(s, Starter.LOG_LEVEL.DEBUG);
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        Starter.getInstance().log(this.getClass().getSimpleName() + ": " + s, level);
    }

    public enum State {
        STARTING,
        AWAITING_LEAGUE_PROCESS,
        NO_PROCESS_IDLE,
        AWAITING_LCU_CONNECTION,
        AWAITING_LCU_INIT,
        CONNECTED,
        DISCONNECTED,
        STOPPING;

        private State[] transitions;

        State() {
        }

        public State[] getTransitions() {
            return transitions;
        }

        private void setTransitions(State[] transitions) {
            this.transitions = transitions;
        }

        public boolean isValidTransition(State newState) {
            for (State state : transitions) {
                if (state == newState) {
                    return true;
                }
            }
            return false;
        }
    }
}


