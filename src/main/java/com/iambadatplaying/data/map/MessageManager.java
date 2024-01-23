package com.iambadatplaying.data.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import com.iambadatplaying.structs.messaging.Conversation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager extends  MapDataManager<String> {

    public static final String CURRENT_CHAMP_SELECT = "CHAMP_SELECT";

    private static final String SYSTEM_MESSAGE = "system";

    private static final String SYSTEM_LEFT_ROOM = "left_room";

    String conversationIdRegex = "/lol-chat/v1/conversations/([^/]+)";

    Pattern conversationIdPattern = Pattern.compile(conversationIdRegex);

    public MessageManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        if (uri.startsWith("/lol-chat/v1/conversations/")) {
            return true;
        }
        return false;
    }

    private Optional<String> extractConversationId(String uri) {
        Matcher matcher = conversationIdPattern.matcher(uri);
        if (matcher.find()) {

            String conversationId = matcher.group(1);

            try {
                if (!conversationId.contains("@")) {
                    conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
                }
                return Optional.of(conversationId);
            } catch (Exception e) {

            }

        }
        return Optional.empty();
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case "Delete":
                if (uri.contains("/messages")) return;
                log("Registered conversation deletion", Starter.LOG_LEVEL.DEBUG);
                Optional<String> conversationId = extractConversationId(uri);
                if (conversationId.isPresent()) {
                    log("Conversation " + conversationId.get() + " deleted, removing from cache", Starter.LOG_LEVEL.DEBUG);
                    map.remove(conversationId.get());
                }
                break;
            case "Create":
            case "Update":
                //I swear to god riot, why do you have to make this so complicated
                //Peer to peer messages are updated via single messages, only override happens on init
                //For some god forsaken reason, group messages are updated via a message array, that replaces the current one AND via single messages
                //I should really look into the RTMP in the lower levels but this is beyond the scope of this project
                Optional<String> updateConversationId = extractConversationId(uri);
                if (uri.contains("/participants")) return;
                if (!updateConversationId.isPresent()) {
                    return;
                }
                String conversationIdStr = updateConversationId.get();
                if (!uri.contains("/messages")) return;
                if (uri.endsWith("/messages")) { //Contains a message array that replaces the current one
                    log("Conversation " + conversationIdStr + " updated via message array", Starter.LOG_LEVEL.DEBUG);
                    handleMessageArray(conversationIdStr, data.getAsJsonArray());
                } else { //We expect a single message, add it to the array
                    log("Conversation " + conversationIdStr + " updated via single message", Starter.LOG_LEVEL.DEBUG);
                    handleSingleMessage(conversationIdStr, data.getAsJsonObject());
                }

                break;
        }
    }

    private void handleMessageArray(String conversationId, JsonArray messages) {
        JsonObject conversation = map.get(conversationId);
        if (conversation == null) {
            conversation = fetchConversation(conversationId);
            if (conversation == null) {
                log("Failed to initialize conversation " + conversationId, Starter.LOG_LEVEL.WARN);
                return;
            }
        }
        conversation.add("messages", messages);
        updateMap(conversationId, conversation);
    }

    private void handleSingleMessage(String conversationId, JsonObject messageData) {
        if (!Util.jsonKeysPresent(messageData, "body", "type","id")) {
            log("Message " + messageData.get("id").getAsString() + " is missing required fields", Starter.LOG_LEVEL.DEBUG);
            return;
        }
        String type = messageData.get("type").getAsString();
        String body = messageData.get("body").getAsString();
        //IF the message is a system message and of type "left_room" we know this conversation is ending, we dont initialize it
        if (type.equals(SYSTEM_MESSAGE) && body.equals(SYSTEM_LEFT_ROOM)) {
            log("Conversation " + conversationId + " ended, will not update", Starter.LOG_LEVEL.DEBUG);
            return;
        }
        //Otherwise we try to get the conversation, if it doesnt exist we initialize it
        JsonObject conversation = map.get(conversationId);
        if (conversation == null) {
            log("Conversation " + conversationId + " not found, initializing", Starter.LOG_LEVEL.DEBUG);
            conversation = fetchConversation(conversationId);
            if (conversation == null) {
                log("Failed to initialize conversation " + conversationId, Starter.LOG_LEVEL.WARN);
                return;
            }
        }
        String currentMessageId = messageData.get("id").getAsString();
        JsonArray previousMessages = conversation.get("messages").getAsJsonArray();
        //We will just check the last 10 messages for duplicates, if we find one we dont add it
        for (int i = previousMessages.size()-1 ; i >= 0 && i >= previousMessages.size()-10; i--) {
            JsonObject previousMessage = previousMessages.get(i).getAsJsonObject();
            if (previousMessage.get("id").getAsString().equals(currentMessageId)) {
                log("Conversation " + conversationId + " already contains message " + currentMessageId + ", will not update", Starter.LOG_LEVEL.DEBUG);
                updateMap(conversationId, conversation);
                return;
            }
        }
        previousMessages.add(messageData);

        updateMap(conversationId, conversation);
    }

    private void updateMap(String conversationId, JsonObject conversation) {
        map.put(conversationId, conversation);
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(ReworkedDataManager.UPDATE_TYPE_CONVERSATION, conversation));
    }


    @Override
    protected void doShutdown() {

    }

    @Override
    public JsonObject load(String key) {
        Optional<String> conversationId = extractConversationId(key);
        if (conversationId.isPresent()) {
            return fetchConversation(conversationId.get());
        }
        return new JsonObject();
    }

    private JsonObject fetchConversation(String conversationId) {
        JsonObject initialFetchJson = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-chat/v1/conversations/" + conversationId));
        if (initialFetchJson.has("httpStatus") && initialFetchJson.get("httpStatus").getAsInt() == 404) {
            initialFetchJson = initConversation(conversationId);
        }
        JsonElement messagesElement = ConnectionManager.getResponseBodyAsJsonElement(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-chat/v1/conversations/" + conversationId + "/messages"));
        if (!messagesElement.isJsonArray()) {
            log("Failed to fetch messages for conversation " + conversationId, Starter.LOG_LEVEL.ERROR);
            return null;
        }
        JsonArray messages = messagesElement.getAsJsonArray();
        initialFetchJson.add("messages", messages);
        return initialFetchJson;
    }

    private JsonObject initConversation(String conversationId) {
        log("Conversation " + conversationId + " not found, trying to initialize it", Starter.LOG_LEVEL.INFO);
        JsonObject initConversationJson = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-chat/v1/conversations/" + conversationId));
        return initConversationJson;
    }
}
