package com.iambadatplaying.structs.messaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class Message {
    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_GROUPCHAT = "groupchat";
    public static final String TYPE_CELEBRATION = "celebration";

    public static final String MESSAGE_LEFT = "left_room";
    public static final String MESSAGE_JOINED = "joined_room";

    public static final String ID = "id";
    public static final String AUTHOR_PUUID = "fromPid";
    public static final String OBFUSCATED_AUTHOR_ID = "obfuscatedAuthorId";
    public static final String BODY = "body";
    public static final String TIMESTAMP = "timestamp";
    public static final String TYPE = "type";

    private String authorPuuid;
    private String authorId;
    private String obfuscatedAuthorId;
    private String body;
    private String timestamp;
    private String type;
    private String id;

    public Message(String id) {
        this.id = id;
    }

    public boolean isSystemMessage() {
        return TYPE_SYSTEM.equals(this.type);
    }

    public static Message createCelebrationMessage(String body) {
        Message message = new Message(null);
        message.setBody(body);
        message.setType(TYPE_CELEBRATION);
        return message;
    }

    public static ArrayList<Message> createMessageList(JSONArray jsonMessages) {
        ArrayList<Message> messages = new ArrayList<>();
        if (jsonMessages == null) return messages;
        for (int i = 0; i < jsonMessages.length(); i++) {
            JSONObject jsonMessage = jsonMessages.getJSONObject(i);
            Message message = Message.fromJsonObject(jsonMessage);
            if (message == null) continue;
            messages.add(message);
        }
        return messages;
    }

    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(ID, this.id);
        jsonObject.put(AUTHOR_PUUID, this.authorPuuid);
        jsonObject.put(OBFUSCATED_AUTHOR_ID, this.obfuscatedAuthorId);
        jsonObject.put(BODY, this.body);
        jsonObject.put(TIMESTAMP, this.timestamp);
        jsonObject.put(TYPE, this.type);
        return jsonObject;
    }

    public static Message fromJsonObject(JSONObject jsonMessage) {
        if(jsonMessage == null) return null;
        if (jsonMessage.has(ID)) {
            Message message = new Message(jsonMessage.getString(ID));
            if(jsonMessage.has(AUTHOR_PUUID)) message.setAuthorPuuid(jsonMessage.getString(AUTHOR_PUUID));
            if (jsonMessage.has(OBFUSCATED_AUTHOR_ID)) message.setObfuscatedAuthorId(jsonMessage.getString(OBFUSCATED_AUTHOR_ID));
            if (jsonMessage.has(BODY)) message.setBody(jsonMessage.getString(BODY));
            if (jsonMessage.has(TIMESTAMP)) message.setTimestamp(jsonMessage.getString(TIMESTAMP));
            if (jsonMessage.has(TYPE)) message.setType(jsonMessage.getString(TYPE));
            return message;
        } return null;
    }

    public void setAuthorPuuid(String authorPuuid) {
        this.authorPuuid = authorPuuid;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public void setObfuscatedAuthorId(String obfuscatedAuthorId) {
        this.obfuscatedAuthorId = obfuscatedAuthorId;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAuthorPuuid() {
        return authorPuuid;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getObfuscatedAuthorId() {
        return obfuscatedAuthorId;
    }

    public String getBody() {
        return body;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Message) {
            Message otherMessage = (Message) obj;
            return this.getId().equals(otherMessage.getId());
        }
        return false;
    }
}

