package com.iambadatplaying.tasks.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ReworkedChampSelectData;
import com.iambadatplaying.data.state.StateDataManager;
import com.iambadatplaying.rest.servlets.ChampSelectServlet;
import com.iambadatplaying.tasks.ARGUMENT_TYPE;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.builders.TaskArgumentBuilder;
import com.iambadatplaying.tasks.builders.impl.NumberDataBuilder;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class AutoPickChamp extends Task {

    private final String lol_champ_select_v1_session = "/lol-champ-select/v1/session";

    private volatile boolean alreadyPicked = false;

    private Integer championId;
    private Integer delay;
    private Timer timer;

    private static final String DESCRIPTION = "Automatically picks a champion.";

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
            StateDataManager champSelectManager = starter.getDataManager().getStateManager(ReworkedChampSelectData.class);
            if (champSelectManager == null) return;
            Optional<JsonObject> currentInternalState = champSelectManager.getCurrentState();
            if (!currentInternalState.isPresent()) return;
            JsonObject currentChampSelectState = currentInternalState.get();
            Optional<Integer> optLocalPlayerCellId = Util.getOptInt(currentChampSelectState, "localPlayerCellId");
            if (!optLocalPlayerCellId.isPresent()) return;
            int localPlayerCellId = optLocalPlayerCellId.get();

            Optional<JsonArray> optMyTeam = Util.getOptJSONArray(currentChampSelectState, "myTeam");
            if (!optMyTeam.isPresent()) return;

            JsonArray myTeam = optMyTeam.get();
            for (int i = 0, arrayLength = myTeam.size(); i < arrayLength; i++) {
                JsonObject player = myTeam.get(i).getAsJsonObject();
                if (player.isEmpty()) continue;
                if (!Util.jsonKeysPresent(player, "cellId")) continue;
                if (player.get("cellId").getAsInt() == localPlayerCellId) {
                    if (Util.jsonKeysPresent(player, "pickAction")) {
                        log("Got here 3");
                        JsonObject pickAction = player.get("pickAction").getAsJsonObject();
                        if (!Util.jsonKeysPresent(pickAction, "isInProgress", "id")) continue;
                        if (!pickAction.get("isInProgress").getAsBoolean()) continue;
                        scheduleLockIn(championId);
                        break;
                    }
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
                    new ChampSelectServlet().pickChampion(action);
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
        if (timer != null) {
            timer.cancel();
        }
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
            log("Failed to set Task-Args for Task " + this.getClass().getSimpleName(), Starter.LOG_LEVEL.ERROR);
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

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Delay")
                        .setBackendKey("delay")
                        .setType(ARGUMENT_TYPE.NUMBER)
                        .setRequired(true)
                        .setAdditionalData(
                                new NumberDataBuilder()
                                        .setMinimumValue(0)
                                        .setMaximumValue(60_000)
                                        .build()
                        )
                        .setCurrentValue(this.delay)
                        .setDescription("Time until the champion gets picked in milliseconds")
                        .build()
        );

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Champion ID")
                        .setBackendKey("championId")
                        .setType(ARGUMENT_TYPE.OWNED_CHAMPION_SELECT)
                        .setRequired(true)
                        .setAdditionalData(
                                new NumberDataBuilder()
                                        .setMinimumValue(0)
                                        .setMaximumValue(999_999)
                                        .build()
                        )
                        .setCurrentValue(this.championId)
                        .setDescription("The ID of the champion you want to pick")
                        .build()
        );

        return requiredArgs;
    }


    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
}
