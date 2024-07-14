package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.DataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.iambadatplaying.lcuHandler.ConnectionManager.conOptions.GET;

public class CurrentSummonerManager extends StateDataManager {

    private static final String UPDATE_TYPE_CURRENT_SUMMONER = DataManager.UPDATE_TYPE_CURRENT_SUMMONER;

    private static final Pattern LOL_SUMMONER_PATTERN = Pattern.compile("/lol-summoner/v1/current-summoner$");

    public CurrentSummonerManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {
    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return LOL_SUMMONER_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
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
        HttpsURLConnection con = starter.getConnectionManager().buildConnection(GET, "/lol-summoner/v1/current-summoner");
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
