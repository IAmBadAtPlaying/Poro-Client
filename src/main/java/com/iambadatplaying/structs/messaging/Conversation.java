package com.iambadatplaying.structs.messaging;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;

public class Conversation {
    private static final String ID = "id";
    private static final String PID = "pid";
    private static final String TYPE = "type";
    private static final String UNREAD_MESSAGE_COUNT = "unreadMessageCount";
    private static final String MESSAGES = "messages";
    private static final String SCOPE_CHAMP_SELECT = "champ-select";
    private static final String SCOPE_LOBBY = "sec";
    private static final String SCOPE_POST_GAME = "post-game";
    private static final String SCOPE_CLASH = "clash";
    private ArrayList<Message> messages = new ArrayList<>();
    private String id;
    private String pid;
    private String type;
    private Integer unreadMessageCount;
    private final SCOPE scope;
    public Conversation(String id) {
        if (id == null) throw new IllegalArgumentException("Conversation id cannot be null");
        this.id = id;
        if (id.contains(SCOPE_CHAMP_SELECT)) {
            this.scope = SCOPE.CHAMP_SELECT;
        } else if (id.contains(SCOPE_CLASH)) {
            this.scope = SCOPE.CLASH;
        } else if (id.contains(SCOPE_LOBBY)) {
            this.scope = SCOPE.LOBBY;
        } else if (id.contains(SCOPE_POST_GAME)) {
            this.scope = SCOPE.POST_GAME;
        } else {
            this.scope = SCOPE.PEER_TO_PEER;
        }
    }

    public static Conversation fromJsonObject(JsonObject jsonConversation) {
        if (jsonConversation == null) return null;
        if (!jsonConversation.has(ID)) return null;
        Conversation conversation = new Conversation(jsonConversation.get("id").getAsString());
        if (jsonConversation.has(PID)) conversation.setPid(jsonConversation.get("pid").getAsString());
        if (jsonConversation.has(TYPE)) conversation.setType(jsonConversation.get("type").getAsString());
        if (jsonConversation.has(UNREAD_MESSAGE_COUNT))
            conversation.setUnreadMessageCount(jsonConversation.get("unreadMessageCount").getAsInt());
        return conversation;
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public void addMessages(ArrayList<Message> messages) {
        this.messages.addAll(messages);
    }

    public ArrayList<Message> getMessages() {
        return messages;
    }

    public Message getLastMessage() {
        if (!messages.isEmpty()) {
            return messages.get(messages.size() - 1);
        } else return null;
    }

    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(ID, this.getId());
        jsonObject.addProperty(PID, this.getPid());
        jsonObject.addProperty(TYPE, this.getType());
        jsonObject.addProperty(UNREAD_MESSAGE_COUNT, this.getUnreadMessageCount());

        JsonArray messages = new JsonArray();
        for (Message message : this.messages) {
            messages.add(message.toJsonObject());
        }
        jsonObject.add(MESSAGES, messages);
        return jsonObject;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getUnreadMessageCount() {
        return unreadMessageCount;
    }

    public void setUnreadMessageCount(Integer unreadMessageCount) {
        this.unreadMessageCount = unreadMessageCount;
    }

    public SCOPE getScope() {
        return scope;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof Conversation)) return false;
        Conversation otherConversation = (Conversation) other;
        return this.getId().equals(otherConversation.getId());
    }

    public void overrideMessages(ArrayList<Message> messages) {
        this.messages = messages;
    }

    public enum SCOPE {
        LOBBY,
        CHAMP_SELECT,
        CLASH,
        POST_GAME,
        PEER_TO_PEER;

        public SCOPE fromString(String s) {
            if (s == null) return null;
            switch (s.toLowerCase()) {
                case SCOPE_CHAMP_SELECT:
                    return CHAMP_SELECT;
                case SCOPE_LOBBY:
                    return LOBBY;
                case SCOPE_POST_GAME:
                    return POST_GAME;
                default:
                    return PEER_TO_PEER;
            }
        }
    }
}
