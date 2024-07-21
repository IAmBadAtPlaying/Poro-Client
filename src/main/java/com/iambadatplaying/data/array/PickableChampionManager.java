package com.iambadatplaying.data.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PickableChampionManager extends ArrayDataManager {

    private static final Pattern PICKABLE_CHAMPION_PATTERN = Pattern.compile("/lol-champ-select/v1/pickable-champion-ids$");

    public PickableChampionManager(Starter starter) {
        super(starter);
    }

    @Override
    protected Optional<JsonArray> fetchCurrentState() {
        JsonElement data = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-champ-select/v1/pickable-champion-ids"));
        if (!data.isJsonArray()) return Optional.empty();
        return Optional.of(data.getAsJsonArray());
    }

    @Override
    public void sendCurrentState() {

    }

    @Override
    public String getEventName() {
        return "";
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return PICKABLE_CHAMPION_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
                resetState();
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                if (!data.isJsonArray()) return;
                JsonArray updatedState = data.getAsJsonArray();
                if (Util.equalJsonElements(updatedState, currentArray)) return;
                currentArray = updatedState;
                sendCurrentState();
                break;
        }
    }

    @Override
    protected void doShutdown() {

    }
}
