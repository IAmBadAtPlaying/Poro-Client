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
        if (mainInitiator == null || !mainInitiator.isRunning()) {
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
    public void setTaskArgs(JSONObject arguments) {
        try {
            delay = arguments.getInt("delay");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}
