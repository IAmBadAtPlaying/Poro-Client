package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.DataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatcherData extends StateDataManager {

    private final static Pattern PATCHER_PATTERN = Pattern.compile("/patcher/v1/products/league_of_legends/state$");

    public PatcherData(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return PATCHER_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                if (!data.isJsonObject()) return;
                JsonObject updatedState = data.getAsJsonObject();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/patcher/v1/products/league_of_legends/state"));
        if (!data.has("errorCode")) return Optional.of(data);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(DataManager.UPDATE_TYPE_PATCHER, currentState));
    }

    @Override
    public String getEventName() {
        return DataManager.UPDATE_TYPE_PATCHER;
    }
}
