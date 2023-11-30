package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.ressourceServer.ProxyHandler;
import com.iambadatplaying.ressourceServer.ResourceServer;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AutoPickChamp extends Task {

    private final String lol_champ_select_v1_session = "/lol-champ-select/v1/session";

    private volatile boolean alreadyPicked;

    private Integer championId;
    private Integer delay;
    private Timer timer;

    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        JSONObject data = webSocketEvent.getJSONObject(2);
        String uriTrigger = data.getString("uri");
        switch (uriTrigger) {
            case lol_champ_select_v1_session:
                handleUpdateData(data);
        }
    }

    private void handleUpdateData(JSONObject updateData) {
        try {
            if ("Create".equals(updateData.getString("eventType"))) { //A new ChampSelect Instance has started, we reset the picked status
                resetChampSelectVariables();
                return;
            }
            if (!alreadyPicked) {
                scheduleLockIn(championId);
            } else log("Already picked, skipping", MainInitiator.LOG_LEVEL.DEBUG);
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
                        log("Trying to invoke champion Pick", MainInitiator.LOG_LEVEL.DEBUG);
                        JSONObject action = new JSONObject();
                        action.put("championId", championId);
                        action.put("lockIn", true);
                        HttpURLConnection proxyCon = (HttpURLConnection) new URL("http://localhost:"+ MainInitiator.RESSOURCE_SERVER_PORT +"/rest/champSelect/pick").openConnection();
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
        log("Resetting Champ-Select", MainInitiator.LOG_LEVEL.DEBUG);
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

    public boolean setTaskArgs(JSONObject arguments) {
        try {
            delay = arguments.getInt("delay");
            championId = arguments.getInt("championId");
            log("Modified Task-Args for Task " + this.getClass().getSimpleName(), MainInitiator.LOG_LEVEL.DEBUG);
            return true;
        } catch (Exception e) {
            mainInitiator.getTaskManager().removeTask(this.getClass().getSimpleName().toLowerCase());
        }
        return false;
    }

    public JSONObject getTaskArgs() {
        JSONObject taskArgs = new JSONObject();
        taskArgs.put("delay", delay);
        taskArgs.put("championId", championId);
        return taskArgs;
    }

    public JSONArray getRequiredArgs() {
        JSONArray requiredArgs = new JSONArray();
        JSONObject delay = new JSONObject();
        delay.put("displayName", "Delay");
        delay.put("backendKey", "delay");
        delay.put("type", INPUT_TYPE.NUMBER.toString());
        delay.put("required", true);
        delay.put("currentValue", this.delay);
        delay.put("description", "Time until the champion gets picked in milliseconds");

        JSONObject championId = new JSONObject();
        championId.put("displayName", "Champion ID");
        championId.put("backendKey", "championId");
        championId.put("type", INPUT_TYPE.NUMBER.toString());
        championId.put("required", true);
        championId.put("currentValue", this.championId);
        championId.put("description", "The ID of the champion you want to pick");

        requiredArgs.put(delay);
        requiredArgs.put(championId);

        return requiredArgs;
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}
