package com.iambadatplaying.data.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChampionInventoryManager extends ArrayDataManager {
    private static final Pattern championInventoryPattern = Pattern.compile("/lol-inventory/v2/inventory/CHAMPION$");

    public ChampionInventoryManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return championInventoryPattern.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
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

    @Override
    protected Optional<JsonArray> fetchCurrentState() {
        HttpsURLConnection con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-inventory/v2/inventory/CHAMPION");
        JsonElement data = ConnectionManager.getResponseBodyAsJsonElement(con);
        if (!data.isJsonArray()) return Optional.empty();
        return Optional.of(data.getAsJsonArray());
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(getEventName(), currentArray));
    }

    @Override
    public String getEventName() {
        return ReworkedDataManager.UPDATE_TYPE_OWNED_CHAMPIONS;
    }

    private void log(String message) {
        log(message, Starter.LOG_LEVEL.DEBUG);
    }

    private void log(String message, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + message, level);
    }
}
