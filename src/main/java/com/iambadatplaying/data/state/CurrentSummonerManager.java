package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;

import static com.iambadatplaying.lcuHandler.ConnectionManager.conOptions.GET;

public class CurrentSummonerManager extends StateDataManager {

    private static final String UPDATE_TYPE_CURRENT_SUMMONER = ReworkedDataManager.UPDATE_TYPE_CURRENT_SUMMONER;

    private static final String RELEVANT_URI = "/lol-summoner/v1/current-summoner";

    public CurrentSummonerManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return RELEVANT_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                if (!data.isJsonObject()) return;
                JsonObject updatedState = data.getAsJsonObject();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
            case UPDATE_TYPE_DELETE:
                resetState();
                break;
        }
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        HttpsURLConnection con = starter.getConnectionManager().buildConnection(GET, RELEVANT_URI);
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(con);
        if (!data.has("errorCode")) return Optional.of(data);
        log("Cant fetch current state: " + data.get("message").getAsString(), Starter.LOG_LEVEL.WARN);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(UPDATE_TYPE_CURRENT_SUMMONER, currentState));
    }

    @Override
    public String getEventName() {
        return UPDATE_TYPE_CURRENT_SUMMONER;
    }
}
