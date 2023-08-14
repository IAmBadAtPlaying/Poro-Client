package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;

public class AutoAcceptQueue implements Task {

    private final String gameflow_v1_gameflow_phase = "OnJsonApiEvent_lol-gameflow_v1_gameflow-phase";
    private final String[] apiTriggerEvents = {gameflow_v1_gameflow_phase};

    private MainInitiator mainInitiator;

    private volatile boolean running = false;

    private Integer delay;
    private Timer timer;

    @Override
    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        String apiTrigger = webSocketEvent.getString(1);
        switch (apiTrigger) {
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
            System.out.println("Gameflow:" + updateData);
            String newGameflowPhase = updateData.getString("data");
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

    @Override
    public String[] getTriggerApiEvents() {
        return apiTriggerEvents;
    }

    @Override
    public void setMainInitiator(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    @Override
    public void init() {
        if (mainInitiator == null) {
            log("No running Main-Initiator present, Task will not start", MainInitiator.LOG_LEVEL.ERROR);
            return;
        }
        running = true;
        timer = new Timer();
        if (delay == null) {
            delay = 0;
        }
    }

    @Override
    public void shutdown() {
        running = false;
        timer.cancel();
        delay = null;
        timer = null;
    }

    @Override
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

    @Override
    public JSONObject getTaskArgs() {
        JSONObject taskArgs = new JSONObject();
        taskArgs.put("delay", delay);
        return taskArgs;
    }

    @Override
    public JSONArray getRequiredArgs() {
        JSONArray requiredArgs = new JSONArray();

        JSONObject delay = new JSONObject();
        delay.put("displayName", "Delay");
        delay.put("description", "Time till Ready-Check gets accepted in ms");
        delay.put("type", "Integer");
        delay.put("required", true);
        delay.put("currentValue", this.delay);
        delay.put("backendKey", "delay");

        requiredArgs.put(delay);

        return requiredArgs;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}
