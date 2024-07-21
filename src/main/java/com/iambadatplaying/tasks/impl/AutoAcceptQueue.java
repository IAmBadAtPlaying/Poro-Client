package com.iambadatplaying.tasks.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.tasks.ARGUMENT_TYPE;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.builders.TaskArgumentBuilder;
import com.iambadatplaying.tasks.builders.impl.NumberDataBuilder;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class AutoAcceptQueue extends Task {

    private final String gameflow_v1_gameflow_phase = "/lol-gameflow/v1/session";
    private final String[] apiTriggerEvents = {gameflow_v1_gameflow_phase};

    private Integer delay;
    private Timer timer;

    private static final String DESCRIPTION = "Automatically accepts the ready check as soon as it appears.";

    public void notify(JsonArray webSocketEvent) {
        if (!running || starter == null || webSocketEvent.isEmpty() || webSocketEvent.size() < 3) {
            return;
        }
        JsonObject data = webSocketEvent.get(2).getAsJsonObject();
        String uriTrigger = data.get("uri").getAsString();
        switch (uriTrigger) {
            case gameflow_v1_gameflow_phase:
                JsonObject updateObject = webSocketEvent.get(2).getAsJsonObject();
                handleUpdateData(updateObject);
                break;
            default:
                break;
        }
    }

    private void handleUpdateData(JsonObject updateData) {
        try {
            JsonObject data = updateData.get("data").getAsJsonObject();
            String newGameflowPhase = data.get("phase").getAsString();
            if ("ReadyCheck".equals(newGameflowPhase)) {
                Timer timer = new java.util.Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            HttpURLConnection con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-matchmaking/v1/ready-check/accept", "{}");
                            con.getResponseCode();
                            con.disconnect();
                        } catch (Exception e) {

                        }
                    }
                }, delay);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void doInitialize() {
        timer = new Timer();
        if (delay == null) {
            delay = 0;
        }
    }

    protected void doShutdown() {
        if (timer != null) {
            timer.cancel();
        }
        delay = null;
        timer = null;
    }

    public boolean setTaskArgs(JsonObject arguments) {
        try {
            log(arguments.toString(), Starter.LOG_LEVEL.DEBUG);
            delay = arguments.get("delay").getAsInt();
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
        return taskArgs;
    }

    public JsonArray getRequiredArgs() {
        JsonArray requiredArgs = new JsonArray();

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Delay")
                        .setBackendKey("delay")
                        .setType(ARGUMENT_TYPE.NUMBER)
                        .setAdditionalData(
                                new NumberDataBuilder()
                                        .setMinimumValue(0)
                                        .setMaximumValue(12_000)
                                        .build()
                        )
                        .setRequired(true)
                        .setCurrentValue(this.delay)
                        .build()
        );

        return requiredArgs;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

}
