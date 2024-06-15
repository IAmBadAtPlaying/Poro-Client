package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ReworkedChampSelectData;
import com.iambadatplaying.data.state.StateDataManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class AutoPickChamp extends Task {

    private final String lol_champ_select_v1_session = "/lol-champ-select/v1/session";

    private volatile boolean alreadyPicked;

    private Integer championId;
    private Integer delay;
    private Timer timer;

    public void notify(JsonArray webSocketEvent) {
        if (!running || starter == null || webSocketEvent.isEmpty() || webSocketEvent.size() < 3) {
            return;
        }
        JsonObject data = webSocketEvent.get(2).getAsJsonObject();
        String uriTrigger = data.get("uri").getAsString();
        switch (uriTrigger) {
            case lol_champ_select_v1_session:
                handleUpdateData(data);
        }
    }

    private void handleUpdateData(JsonObject updateData) {
        try {
            if ("Create".equals(updateData.get("eventType").getAsString())) { //A new ChampSelect Instance has started, we reset the picked status
                resetChampSelectVariables();
                return;
            }
            if (alreadyPicked) return;
            StateDataManager champSelectManager = starter.getReworkedDataManager().getStateManagers(ReworkedChampSelectData.class);
            if (champSelectManager == null) return;
            Optional<JsonObject> currentInternalState = champSelectManager.getCurrentState();
            if (!currentInternalState.isPresent()) return;
            JsonObject currentChampSelectState = currentInternalState.get();
            Optional<Integer> optLocalPlayerCellId = Util.getOptInt(currentChampSelectState, "localPlayerCellId");
            if (!optLocalPlayerCellId.isPresent()) return;
            Integer localPlayerCellId = optLocalPlayerCellId.get();

            Optional<JsonArray> optMyTeam = Util.getOptJSONArray(currentChampSelectState, "myTeam");
            if (!optMyTeam.isPresent()) return;

            JsonArray myTeam = optMyTeam.get();
            for (int i = 0, arrayLength = myTeam.size(); i < arrayLength; i++) {
                JsonObject player = myTeam.get(i).getAsJsonObject();
                if (player.isEmpty()) continue;
                if (!Util.jsonKeysPresent(player, "cellId")) continue;
                if (player.get("cellId").getAsInt() == localPlayerCellId) {
                    JsonObject pickAction = player.get("pickAction").getAsJsonObject();
                    if (!Util.jsonKeysPresent(pickAction, "isInProgress", "id")) continue;
                    if (!pickAction.get("isInProgress").getAsBoolean()) continue;
                    scheduleLockIn(championId);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void scheduleLockIn(Integer championId) {
        alreadyPicked = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    log("Trying to invoke champion Pick", Starter.LOG_LEVEL.DEBUG);
                    JsonObject action = new JsonObject();
                    action.addProperty("championId", championId);
                    action.addProperty("lockIn", true);
                    HttpURLConnection proxyCon = (HttpURLConnection) new URL("http://localhost:" + Starter.RESSOURCE_SERVER_PORT + "/rest/champSelect/pick").openConnection();
                    proxyCon.setRequestMethod("POST");
                    proxyCon.setDoOutput(true);
                    proxyCon.getOutputStream().write(action.toString().getBytes());
                    proxyCon.getResponseCode();
                    proxyCon.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, delay);
    }

    private void resetChampSelectVariables() {
        log("Resetting Champ-Select", Starter.LOG_LEVEL.DEBUG);
        alreadyPicked = false;
    }

    protected void doInitialize() {
        timer = new Timer();
        alreadyPicked = false;
        running = true;
    }

    public void doShutdown() {
        timer.cancel();
        alreadyPicked = false;
        timer = null;
    }

    public boolean setTaskArgs(JsonObject arguments) {
        try {

            int delay = arguments.get("delay").getAsInt();
            int championId = arguments.get("championId").getAsInt();

            this.delay = delay;
            this.championId = championId;
            log("Modified Task-Args for Task " + this.getClass().getSimpleName(), Starter.LOG_LEVEL.DEBUG);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public JsonObject getTaskArgs() {
        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("delay", delay);
        taskArgs.addProperty("championId", championId);
        return taskArgs;
    }

    public JsonArray getRequiredArgs() {
        JsonArray requiredArgs = new JsonArray();
        JsonObject delay = new JsonObject();
        delay.addProperty("displayName", "Delay");
        delay.addProperty("backendKey", "delay");
        delay.addProperty("type", INPUT_TYPE.NUMBER.toString());
        delay.addProperty("required", true);
        delay.addProperty("currentValue", this.delay);
        delay.addProperty("description", "Time until the champion gets picked in milliseconds");

        JsonObject championId = new JsonObject();
        championId.addProperty("displayName", "Champion ID");
        championId.addProperty("backendKey", "championId");
        championId.addProperty("type", INPUT_TYPE.CHAMPION_SELECT.toString());
        championId.addProperty("required", true);
        championId.addProperty("currentValue", this.championId);
        championId.addProperty("description", "The ID of the champion you want to pick");

        requiredArgs.add(delay);
        requiredArgs.add(championId);

        return requiredArgs;
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getName() + ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getName() + ": " + s);
    }

}
