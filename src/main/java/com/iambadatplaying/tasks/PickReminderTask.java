package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

public class PickReminderTask extends Task{

    private static final String typeChampSelect = "championSelect";

    private static final String lol_chat_v1_conversations = "/lol-chat/v1/conversations/";

    private volatile boolean alreadyReminded = false;

    private Timer timer;

    private String currentChatId;

    @Override
    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        JSONObject data = webSocketEvent.getJSONObject(2);
        String uriTrigger = data.getString("uri");
        if (uriTrigger.startsWith("/lol-chat/v1/conversations/")) {
            handleUpdateData(data);
        }
    }

    private void handleUpdateData(JSONObject jsonData) {
        String eventType = jsonData.getString("eventType");
        switch (eventType) {
            case "Create":
            case "Update":
                if (!alreadyReminded) {
                    String chatId = getRoomId(jsonData.getString("uri"));
                    if (chatId.isEmpty()) return;

                    currentChatId = chatId;
                    StringBuilder sb = new StringBuilder();
                    System.out.println(jsonData.getJSONObject("data").toString());
                    sb.append("[Poro-Client] You are playing with\n");

                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            JSONObject participants = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildRiotConnection(ConnectionManager.conOptions.GET, "/chat/v5/participants/champ-select", ""));
                            if (participants.has("participants")) {
                                JSONArray participantsArray = participants.getJSONArray("participants");
                                for (int i = 0; i < participantsArray.length(); i++) {
                                    JSONObject participant = participantsArray.getJSONObject(i);
                                    if (participant.has("name")) {
                                        String gameName = participant.getString("name");
                                        sb.append(gameName);
                                        sb.append("\n");
                                    }
                                }
                            }

                            JSONObject reminderMessage = new JSONObject();
                            reminderMessage.put("type", "celebration");
                            reminderMessage.put("body", sb.toString());

                            mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST,"/lol-chat/v1/conversations/" + chatId + "/messages", reminderMessage.toString()));
                        }
                    }, 2000);

                    alreadyReminded = true;
                }
            break;
            case "Delete":
                if (getRoomId(jsonData.getString("uri")).equals(currentChatId)) {
                    alreadyReminded = false;
                }
            break;
            default: break;
        }
    }

    private String getRoomId(String uri) {
        if (uri == null || uri.isEmpty()) return "";
        String assumedUri = uri.substring(lol_chat_v1_conversations.length());
        if (assumedUri.contains("%40champ-select") && !assumedUri.contains("/")) {
            return assumedUri;
        }
        return "";
    }

    @Override
    protected void doInitialize() {
        currentChatId = "";
        timer = new Timer();
    }

    @Override
    protected void doShutdown() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
        currentChatId = null;
    }

    @Override
    public boolean setTaskArgs(JSONObject arguments) {
        return true;
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
