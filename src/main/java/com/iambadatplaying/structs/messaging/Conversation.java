package com.iambadatplaying.structs.messaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class Conversation {
    public enum SCOPE {
        LOBBY,
        CHAMP_SELECT,
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

    private ArrayList<Message> messages = new ArrayList<>();

    private String id;
    private String pid;
    private String type;
    private Integer unreadMessageCount;

    private SCOPE scope;

    private static final String ID = "id";
    private static final String PID = "pid";
    private static final String TYPE = "type";
    private static final String UNREAD_MESSAGE_COUNT = "unreadMessageCount";

    private static final String MESSAGES = "messages";

    private static final String SCOPE_CHAMP_SELECT = "champ-select";
    private static final String SCOPE_LOBBY = "sec";
    private static final String SCOPE_POST_GAME = "post-game";


    public Conversation(String id) {
        if (id == null) throw new IllegalArgumentException("Conversation id cannot be null");
        this.id = id;
        if (id.contains(SCOPE_CHAMP_SELECT)) {
            this.scope = SCOPE.CHAMP_SELECT;
        } else if (id.contains(SCOPE_LOBBY)) {
            this.scope = SCOPE.LOBBY;
        } else if (id.contains(SCOPE_POST_GAME)) {
            this.scope = SCOPE.POST_GAME;
        } else {
            this.scope = SCOPE.PEER_TO_PEER;
        }
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

    public static Conversation fromJsonObject(JSONObject jsonConversation) {
        if (jsonConversation == null) return null;
        if (!jsonConversation.has(ID)) return null;
        Conversation conversation = new Conversation(jsonConversation.getString("id"));
        if (jsonConversation.has(PID)) conversation.setPid(jsonConversation.getString("pid"));
        if (jsonConversation.has(TYPE)) conversation.setType(jsonConversation.getString("type"));
        if (jsonConversation.has(UNREAD_MESSAGE_COUNT)) conversation.setUnreadMessageCount(jsonConversation.getInt("unreadMessageCount"));
        return conversation;
    }

    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(ID, this.getId());
        jsonObject.put(PID, this.getPid());
        jsonObject.put(TYPE, this.getType());
        jsonObject.put(UNREAD_MESSAGE_COUNT, this.getUnreadMessageCount());

        JSONArray messages = new JSONArray();
        for (Message message : this.messages) {
            messages.put(message.toJsonObject());
        }
        jsonObject.put(MESSAGES, messages);
        return jsonObject;
    }

    public String getId() {
        return id;
    }

    public String getPid() {
        return pid;
    }

    public String getType() {
        return type;
    }

    public Integer getUnreadMessageCount() {
        return unreadMessageCount;
    }

    public SCOPE getScope() {
        return scope;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setUnreadMessageCount(Integer unreadMessageCount) {
        this.unreadMessageCount = unreadMessageCount;
    }

    public void setId(String id) {
        this.id = id;
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
}
