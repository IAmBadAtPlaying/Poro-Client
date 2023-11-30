package com.iambadatplaying.tasks;

import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

public class SuppressUx extends Task {

    private Timer timer;

    @Override
    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        JSONObject data = webSocketEvent.getJSONObject(2);
        String uriTrigger = data.getString("uri");
        if (uriTrigger.startsWith("/riotclient/ux-state/request")) {
            handleUpdateData(data);
        }
    }

    private void handleUpdateData(JSONObject jsonData) {
        String eventType = jsonData.getString("eventType");
        System.out.println(jsonData);
        switch (eventType) {
            case "Create":
            case "Update":
                JSONObject data = jsonData.getJSONObject("data");
                handleUXUpdateJson(data);
                break;
            case "Delete":
            default:
                break;
        }
    }

    private void handleUXUpdateJson(JSONObject data) {
        String state = data.getString("state");
        switch (state) {
            case "ShowMain":
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        killUx();
                    }
                }, 400);
                break;
            default:
                break;
        }
    }

    private void confirmUXLoad() {
        mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT, "/riotclient/ux-load-complete", ""));
    }

    private void killUx() {
        System.out.println("Killing UX");
        System.out.println(mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/riotclient/kill-ux", "")));
    }

    private void showUx() {
        mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/riotclient/launch-ux", ""));
    }

    @Override
    protected void doInitialize() {
        timer = new Timer();
        killUx();
    }

    @Override
    protected void doShutdown() {
        timer.cancel();
        timer = null;
        showUx();
    }

    @Override
    public boolean setTaskArgs(JSONObject arguments) {
        return false;
    }

    @Override
    public JSONObject getTaskArgs() {
        return new JSONObject();
    }

    @Override
    public JSONArray getRequiredArgs() {
        return new JSONArray();
    }
}
