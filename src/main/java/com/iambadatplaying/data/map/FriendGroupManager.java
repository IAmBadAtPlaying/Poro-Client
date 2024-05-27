package com.iambadatplaying.data.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FriendGroupManager extends MapDataManager<Integer> {

    //Well, riot creates and updates group via their id, but the deletion happens via the LITERAL name of the group
    //That means stuff like this /lol-chat/v1/friend-groups//test is valid and will happen, yaay
    private static final Pattern lolChatV1FriendGroupsPattern = Pattern.compile("/lol-chat/v1/friend-groups/(.*)");

    public FriendGroupManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {
        fetchFriendGroups();
    }

    private void fetchFriendGroups() {
        JsonArray friendGroups = ConnectionManager.getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-chat/v1/friend-groups"));
        if (friendGroups == null) return;
        for (int i = 0; i < friendGroups.size(); i++) {
            JsonObject friendGroup = friendGroups.get(i).getAsJsonObject();
            if (!Util.jsonKeysPresent(friendGroup, "id", "name", "priority")) continue;
            int id = friendGroup.get("id").getAsInt();
            map.put(id, friendGroup);
        }
    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return lolChatV1FriendGroupsPattern.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        log("Type: " + type, Starter.LOG_LEVEL.DEBUG);
        switch (type) {
            case UPDATE_TYPE_UPDATE:
            case UPDATE_TYPE_CREATE:
                int id = Integer.parseInt(uriMatcher.group(1));
                log("Updating friend group with id " + id, Starter.LOG_LEVEL.DEBUG);
                map.put(id, data.getAsJsonObject());
                starter.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(ReworkedDataManager.UPDATE_TYPE_FRIEND_GROUPS, data.getAsJsonObject()));
                break;
            case UPDATE_TYPE_DELETE:
                String nameToDelete = uriMatcher.group(1);
                log("Deleting friend group with name " + nameToDelete, Starter.LOG_LEVEL.DEBUG);
                Integer idToDelete = map.entrySet().stream()
                        .filter(entry -> entry.getValue().get("name").getAsString().equals(nameToDelete))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                break;
            default:
                break;
        }
    }

    @Override
    protected void doShutdown() {
    }

    @Override
    public Optional<JsonObject> load(Integer key) {
        HttpsURLConnection connection = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-chat/v1/friend-groups/" + key);
        JsonObject friendGroup = ConnectionManager.getResponseBodyAsJsonObject(connection);
        if (friendGroup == null) return Optional.empty();
        if (Util.jsonKeysPresent(friendGroup,"errorCode")) {
            return Optional.empty();
        }

        map.put(key, friendGroup);
        return Optional.of(friendGroup);
    }
}
