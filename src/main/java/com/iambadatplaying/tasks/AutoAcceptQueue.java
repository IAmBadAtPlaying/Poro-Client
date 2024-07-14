package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;

public class AutoAcceptQueue extends Task {

    private final String gameflow_v1_gameflow_phase = "/lol-gameflow/v1/session";
    private final String[] apiTriggerEvents = {gameflow_v1_gameflow_phase};

    private Integer delay;
    private Timer timer;

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
            starter.getTaskManager().shutdownTask(this.getClass().getSimpleName().toLowerCase().toLowerCase());
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

        JsonObject delay = new JsonObject();
        delay.addProperty("displayName", "Delay");
        delay.addProperty("description", "Time till Ready-Check gets accepted in ms");
        delay.addProperty("type", INPUT_TYPE.NUMBER.toString());
        delay.addProperty("required", true);
        delay.addProperty("currentValue", this.delay);
        delay.addProperty("backendKey", "delay");

        requiredArgs.add(delay);

        return requiredArgs;
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getName() + ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getName() + ": " + s);
    }

}
