package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;

public class AutoPickChamp implements Task {

    private final String lol_champ_select_v1_session = "OnJsonApiEvent_lol-champ-select_v1_session";
    private final String[] apiTriggerEvents = {lol_champ_select_v1_session};

    private MainInitiator mainInitiator;

    private boolean running;

    private volatile boolean alreadyPicked;

    private Integer championId;
    private Integer delay;
    private Timer timer;

    @Override
    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        String apiTrigger = webSocketEvent.getString(1);
        switch (apiTrigger) {
            case lol_champ_select_v1_session:
                JSONObject updateObject = webSocketEvent.getJSONObject(2);
                handleUpdateData(updateObject);
        }
    }

    private void handleUpdateData(JSONObject updateData) {
        try {
            if ("Create".equals(updateData.getString("eventType"))) { //A new ChampSelect Instance has started, we reset the picked status
                resetChampSelectVariables();
                return;
            }
            if (!alreadyPicked) {
                JSONObject data = updateData.getJSONObject("data");
                JSONArray actions = data.getJSONArray("actions");
                if (actions.isEmpty()) {
                    return;
                }
                JSONArray currentAction = actions.getJSONArray(actions.length() -1);
                for (int i = 0; i < currentAction.length(); i++) {
                    JSONObject currentSubAction = currentAction.getJSONObject(i);
                    Boolean isInProgress = currentSubAction.getBoolean("isInProgress");
                    if (isInProgress) {
                        Boolean isAllyAction = currentSubAction.getBoolean("isAllyAction");
                        if (isAllyAction) {
                            Integer actorCellId = currentSubAction.getInt("actorCellId");
                            Integer localPlayerCellId = data.getInt("localPlayerCellId");
                            if (localPlayerCellId.equals(actorCellId)) {
                                String type = currentSubAction.getString("type");
                                if ("pick".equals(type)) {
                                    log("Requirements for champ pick met!", MainInitiator.LOG_LEVEL.DEBUG);
                                    Integer actionId = currentSubAction.getInt("id");
                                    currentSubAction.put("championId", championId);
                                    lockInChampion(currentSubAction, actionId);
                                    return;
                                }
                            }
                        }
                    }
                }
            } else log("Already picked, skipping", MainInitiator.LOG_LEVEL.DEBUG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void lockInChampion(JSONObject action, Integer actionId) {
            alreadyPicked = true;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        log("Trying to invoke champion Pick", MainInitiator.LOG_LEVEL.DEBUG);
                        HttpURLConnection con1 = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH,"/lol-champ-select/v1/session/actions/"+actionId, action.toString());
                        Integer respCode = con1.getResponseCode();
                        if(200 == respCode || 204 == respCode) {
                            HttpURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-champ-select/v1/session/actions/"+actionId+"/complete" , "{}");
                            con.getResponseCode();
                            con.disconnect();
                        }
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
        timer = new Timer();
        alreadyPicked = false;
        running = true;
    }

    @Override
    public void shutdown() {
        running = false;
        timer.cancel();
        alreadyPicked = false;
        timer = null;
    }

    @Override
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

    @Override
    public JSONObject getTaskArgs() {
        JSONObject taskArgs = new JSONObject();
        taskArgs.put("delay", delay);
        taskArgs.put("championId", championId);
        return taskArgs;
    }

    @Override
    public JSONArray getRequiredArgs() {
        JSONArray requiredArgs = new JSONArray();
        JSONObject delay = new JSONObject();
        delay.put("displayName", "Delay");
        delay.put("backendKey", "delay");
        delay.put("type", "Integer");
        delay.put("required", true);
        delay.put("currentValue", this.delay);
        delay.put("description", "Time until the champion gets picked in milliseconds");

        JSONObject championId = new JSONObject();
        championId.put("displayName", "Champion ID");
        championId.put("backendKey", "championId");
        championId.put("type", "Integer");
        championId.put("required", true);
        championId.put("currentValue", this.championId);
        championId.put("description", "The ID of the champion you want to pick");

        requiredArgs.put(delay);
        requiredArgs.put(championId);

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
