package com.iambadatplaying.data.map;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Optional;

public class FriendManager extends MapDataManager<String> {

    private final static String lolChatV1FriendsPattern = "/lol-chat/v1/friends/(.*)";

    public FriendManager(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    @Override
    public JSONObject load(String key) {
        return new JSONObject();
    }

    @Override
    public void doInitialize() {
        fetchFriends();
    }

    private void fetchFriends() {
        JSONArray friends = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-chat/v1/friends"));
        if (friends == null) return;
        for (int i = 0; i < friends.length(); i++) {
            JSONObject friend = friends.getJSONObject(i);
            Optional<JSONObject> optFrontendFriend = backendToFrontendFriend(friend);
            if (!optFrontendFriend.isPresent()) continue;
            JSONObject frontendFriend = optFrontendFriend.get();
            map.put(friend.getString("puuid"), frontendFriend);
        }
    }

    private Optional<JSONObject> backendToFrontendFriend(JSONObject friend) {
        JSONObject frontendFriend = new JSONObject();

        Optional<String> optPuuid = Util.getOptString(friend, "puuid");
        if (!optPuuid.isPresent()) return Optional.empty();
        String puuid = optPuuid.get();
        frontendFriend.put("puuid", puuid);

        Optional<Integer> optIcon = Util.getOptInt(friend, "icon");
        if (optIcon.isPresent()) {
            Integer icon = optIcon.get();
            if (icon < 1) icon = 1;
            frontendFriend.put("icon", icon);
        }

        Util.copyJsonAttributes(friend, frontendFriend, "availability", "statusMessage", "name", "id",  "groupId", "lol");

        return Optional.of(frontendFriend);
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return (uri.matches(lolChatV1FriendsPattern));
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        switch (type) {
            case "Delete":
                String puuidWith = uri.replaceAll(lolChatV1FriendsPattern, "$1");
                Integer atBegin = puuidWith.indexOf("@");
                if (atBegin == -1) return;
                String puuid = puuidWith.substring(0, atBegin);
                map.remove(puuid);
                break;
            case "Create":
            case "Update":
                Optional<JSONObject> updatedFriend = updateFriend(data);
                JSONObject currentState = map.get(data.getString("puuid"));
                if (!updatedFriend.isPresent()) return;
                JSONObject updatedState = updatedFriend.get();
                if (updatedState.similar(currentState)) return;
                map.put(data.getString("puuid"), updatedState);
                mainInitiator.getServer().sendToAllSessions(com.iambadatplaying.lcuHandler.DataManager.getEventDataString("FriendUpdate", updatedState));
                break;
        }
    }

    private Optional<JSONObject> updateFriend(JSONObject friend) {
        Optional<JSONObject> updatedFriend = backendToFrontendFriend(friend);
        return updatedFriend;
    }

    @Override
    public void doShutdown() {
        map.clear();
        map = null;
    }
}
