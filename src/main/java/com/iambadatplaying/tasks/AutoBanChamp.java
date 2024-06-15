package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;

import java.util.Timer;
import java.util.TimerTask;

public class AutoBanChamp extends Task {

    private final String lol_champ_select_v1_session = "/lol-champ-select/v1/session";

    private volatile boolean alreadyBanned;

    private Integer championId;
    private Integer delay;
    private Timer timer;

    @Override
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
        if ("Create".equals(updateData.get("eventType").getAsString())) { //A new ChampSelect Instance has started, we reset the picked status
            resetChampSelectVariables();
        }
    }

    private void resetChampSelectVariables() {
        log("Resetting Champ-Select", Starter.LOG_LEVEL.DEBUG);
        alreadyBanned = false;
    }

    private void scheduleBan(Integer championId) {
        alreadyBanned = true;
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        log("Trying to invoke ban", Starter.LOG_LEVEL.DEBUG);
                        JsonObject action = new JsonObject();
                    }
                },
                delay
        );
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected void doShutdown() {

    }

    @Override
    public boolean setTaskArgs(JsonObject arguments) {
        return false;
    }

    @Override
    public JsonObject getTaskArgs() {
        return null;
    }

    @Override
    public JsonArray getRequiredArgs() {
        return null;
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getName() + ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getName() + ": " + s);
    }

}
