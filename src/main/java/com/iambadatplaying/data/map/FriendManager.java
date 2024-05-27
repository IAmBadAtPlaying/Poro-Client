package com.iambadatplaying.data.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FriendManager extends MapDataManager<String> {

    private static final Pattern lolChatV1FriendsPattern = Pattern.compile("/lol-chat/v1/friends/(.*)");

    public FriendManager(Starter starter) {
        super(starter);
    }

    @Override
    public Optional<JsonObject> load(String key) {
        return Optional.empty();
    }

    @Override
    public void doInitialize() {
        fetchFriends();
    }

    private void fetchFriends() {
        JsonArray friends = starter.getConnectionManager().getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-chat/v1/friends"));
        if (friends == null) return;
        for (int i = 0; i < friends.size(); i++) {
            JsonObject friend = friends.get(i).getAsJsonObject();
            Optional<JsonObject> optFrontendFriend = backendToFrontendFriend(friend);
            if (!optFrontendFriend.isPresent()) continue;
            JsonObject frontendFriend = optFrontendFriend.get();
            map.put(friend.get("puuid").getAsString(), frontendFriend);
        }
    }

    private Optional<JsonObject> backendToFrontendFriend(JsonObject friend) {
        JsonObject frontendFriend = new JsonObject();

        Optional<String> optPuuid = Util.getOptString(friend, "puuid");
        if (!optPuuid.isPresent()) return Optional.empty();
        String puuid = optPuuid.get();
        frontendFriend.addProperty("puuid", puuid);

        Optional<Integer> optIcon = Util.getOptInt(friend, "icon");
        if (optIcon.isPresent()) {
            Integer icon = optIcon.get();
            if (icon < 1) icon = 1;
            frontendFriend.addProperty("iconId", icon);
        }

        Util.copyJsonAttributes(friend, frontendFriend, "availability", "statusMessage", "id", "groupId", "lol", "summonerId", "gameName", "gameTag", "productName", "product");


        return Optional.of(frontendFriend);
    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return lolChatV1FriendsPattern.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
                String puuidWith = uriMatcher.replaceAll("$1");
                Integer atBegin = puuidWith.indexOf("@");
                if (atBegin == -1) return;
                String puuid = puuidWith.substring(0, atBegin);
                map.remove(puuid);
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                Optional<JsonObject> updatedFriend = updateFriend(data);
                if (!updatedFriend.isPresent()) return;
                JsonObject dataObj = data.getAsJsonObject();
                JsonObject currentState = map.get(dataObj.get("puuid").getAsString());
                JsonObject updatedState = updatedFriend.get();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                map.put(dataObj.get("puuid").getAsString(), updatedState);
                starter.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(ReworkedDataManager.UPDATE_TYPE_FRIENDS, updatedState));
                break;
        }
    }

    private Optional<JsonObject> updateFriend(JsonElement friend) {
        if (!friend.isJsonObject()) return Optional.empty();
        JsonObject friendObj = friend.getAsJsonObject();
        Optional<JsonObject> updatedFriend = backendToFrontendFriend(friendObj);
        return updatedFriend;
    }

    @Override
    public void doShutdown() {
    }
}
