package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;

public class AutoAcceptQueue extends Task {

    private final String gameflow_v1_gameflow_phase = "/lol-gameflow/v1/session";
    private final String[] apiTriggerEvents = {gameflow_v1_gameflow_phase};

    private Integer delay;
    private Timer timer;

    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        JSONObject data = webSocketEvent.getJSONObject(2);
        String uriTrigger = data.getString("uri");
        switch (uriTrigger) {
            case gameflow_v1_gameflow_phase:
                JSONObject updateObject = webSocketEvent.getJSONObject(2);
                handleUpdateData(updateObject);
            break;
            default:
            break;
        }
    }

    private void handleUpdateData(JSONObject updateData) {
        try {
            JSONObject data = updateData.getJSONObject("data");
            String newGameflowPhase = data.getString("phase");
            if("ReadyCheck".equals(newGameflowPhase)) {
                Timer timer = new java.util.Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            HttpURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST,"/lol-matchmaking/v1/ready-check/accept","{}");
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
        timer.cancel();
        delay = null;
        timer = null;
    }

    public boolean setTaskArgs(JSONObject arguments) {
        try {
            log(arguments.toString(), MainInitiator.LOG_LEVEL.DEBUG);
            delay = arguments.getInt("delay");
            log("Modified Task-Args for Task " + this.getClass().getSimpleName(), MainInitiator.LOG_LEVEL.DEBUG);
            return true;
        } catch (Exception e) {
            mainInitiator.getTaskManager().removeTask(this.getClass().getSimpleName().toLowerCase().toLowerCase());
        }
        return false;
    }
    public JSONObject getTaskArgs() {
        JSONObject taskArgs = new JSONObject();
        taskArgs.put("delay", delay);
        return taskArgs;
    }

    public JSONArray getRequiredArgs() {
        JSONArray requiredArgs = new JSONArray();

        JSONObject delay = new JSONObject();
        delay.put("displayName", "Delay");
        delay.put("description", "Time till Ready-Check gets accepted in ms");
        delay.put("type", INPUT_TYPE.NUMBER.toString());
        delay.put("required", true);
        delay.put("currentValue", this.delay);
        delay.put("backendKey", "delay");

        requiredArgs.put(delay);

        return requiredArgs;
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}
