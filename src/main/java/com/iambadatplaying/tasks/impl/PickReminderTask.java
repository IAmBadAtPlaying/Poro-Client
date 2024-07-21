package com.iambadatplaying.tasks.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.tasks.Task;

import java.net.URLDecoder;
import java.util.Timer;
import java.util.TimerTask;

public class PickReminderTask extends Task {

    private static final String typeChampSelect = "championSelect";

    private static final String lol_chat_v1_conversations = "/lol-chat/v1/conversations/";

    private volatile boolean alreadyReminded = false;

    private Timer timer;

    private String currentChatId;

    @Override
    public void notify(JsonArray webSocketEvent) {
        if (!running || starter == null || webSocketEvent.isEmpty() || webSocketEvent.size() < 3) {
            return;
        }
        JsonObject data = webSocketEvent.get(2).getAsJsonObject();
        String uriTrigger = data.get("uri").getAsString();
        if (uriTrigger.startsWith("/lol-chat/v1/conversations/")) {
            handleUpdateData(data);
        }
    }

    private void handleUpdateData(JsonObject JsonData) {
        String eventType = JsonData.get("eventType").getAsString();
        switch (eventType) {
            case "Create":
            case "Update":
                if (!alreadyReminded) {
                    String chatId = getRoomId(JsonData.get("uri").getAsString());
                    if (chatId.isEmpty()) return;

                    currentChatId = chatId;
                    StringBuilder sb = new StringBuilder();
                    sb.append("[Poro-Client] You are playing with\n");

                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            JsonObject participants = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildRiotConnection(ConnectionManager.conOptions.GET, "/chat/v5/participants?cid=" + URLDecoder.decode(chatId), ""));
                            if (participants.has("participants")) {
                                JsonArray participantsArray = participants.get("participants").getAsJsonArray();
                                for (int i = 0; i < participantsArray.size(); i++) {
                                    JsonObject participant = participantsArray.get(i).getAsJsonObject();
                                    if (participant.has("name")) {
                                        String gameName = participant.get("game_name").getAsString();
                                        String gameTag = participant.get("game_tag").getAsString();
                                        sb.append(gameName);
                                        sb.append("#");
                                        sb.append(gameTag);
                                        sb.append("\n");
                                    }
                                }
                            }

                            JsonObject reminderMessage = new JsonObject();
                            reminderMessage.addProperty("type", "celebration");
                            reminderMessage.addProperty("body", sb.toString());

                            starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-chat/v1/conversations/" + chatId + "/messages", reminderMessage.toString()));
                        }
                    }, 2000);

                    alreadyReminded = true;
                }
                break;
            case "Delete":
                if (getRoomId(JsonData.get("uri").getAsString()).equals(currentChatId)) {
                    alreadyReminded = false;
                }
                break;
            default:
                break;
        }
    }

    private String getRoomId(String uri) {
        if (uri == null || uri.isEmpty()) return "";
        String assumedUri = uri.substring(lol_chat_v1_conversations.length());
        if (assumedUri.contains("champ-select") && !assumedUri.contains("/")) {
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
    public boolean setTaskArgs(JsonObject arguments) {
        return true;
    }

    @Override
    public JsonObject getTaskArgs() {
        return new JsonObject();
    }

    @Override
    public JsonArray getRequiredArgs() {
        return new JsonArray();
    }
}
