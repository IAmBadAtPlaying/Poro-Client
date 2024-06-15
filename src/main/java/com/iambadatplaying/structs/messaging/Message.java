package com.iambadatplaying.structs.messaging;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
    private final String id;

    public Message(String id) {
        this.id = id;
    }

    public static Message createCelebrationMessage(String body) {
        Message message = new Message(null);
        message.setBody(body);
        message.setType(TYPE_CELEBRATION);
        return message;
    }

    public static ArrayList<Message> createMessageList(JsonArray jsonMessages) {
        ArrayList<Message> messages = new ArrayList<>();
        if (jsonMessages == null) return messages;
        for (int i = 0; i < jsonMessages.size(); i++) {
            JsonObject jsonMessage = jsonMessages.get(i).getAsJsonObject();
            Message message = Message.fromJsonObject(jsonMessage);
            if (message == null) continue;
            messages.add(message);
        }
        return messages;
    }

    public static Message fromJsonObject(JsonObject jsonMessage) {
        if (jsonMessage == null) return null;
        if (jsonMessage.has(ID)) {
            Message message = new Message(jsonMessage.get(ID).getAsString());
            if (jsonMessage.has(AUTHOR_PUUID)) message.setAuthorPuuid(jsonMessage.get(AUTHOR_PUUID).getAsString());
            if (jsonMessage.has(OBFUSCATED_AUTHOR_ID))
                message.setObfuscatedAuthorId(jsonMessage.get(OBFUSCATED_AUTHOR_ID).getAsString());
            if (jsonMessage.has(BODY)) message.setBody(jsonMessage.get(BODY).getAsString());
            if (jsonMessage.has(TIMESTAMP)) message.setTimestamp(jsonMessage.get(TIMESTAMP).getAsString());
            if (jsonMessage.has(TYPE)) message.setType(jsonMessage.get(TYPE).getAsString());
            return message;
        }
        return null;
    }

    public boolean isSystemMessage() {
        return TYPE_SYSTEM.equals(this.type);
    }

    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(ID, this.id);
        jsonObject.addProperty(AUTHOR_PUUID, this.authorPuuid);
        jsonObject.addProperty(OBFUSCATED_AUTHOR_ID, this.obfuscatedAuthorId);
        jsonObject.addProperty(BODY, this.body);
        jsonObject.addProperty(TIMESTAMP, this.timestamp);
        jsonObject.addProperty(TYPE, this.type);
        return jsonObject;
    }

    public String getAuthorPuuid() {
        return authorPuuid;
    }

    public void setAuthorPuuid(String authorPuuid) {
        this.authorPuuid = authorPuuid;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getObfuscatedAuthorId() {
        return obfuscatedAuthorId;
    }

    public void setObfuscatedAuthorId(String obfuscatedAuthorId) {
        this.obfuscatedAuthorId = obfuscatedAuthorId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

