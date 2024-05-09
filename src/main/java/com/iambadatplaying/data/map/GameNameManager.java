package com.iambadatplaying.data.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;

public class GameNameManager extends MapDataManager<String> {

    public GameNameManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return false;
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {

    }

    @Override
    protected void doShutdown() {

    }

    @Override
    public Optional<JsonObject> load(String key) {
        if (key == null || key.isEmpty()) return Optional.empty();
        HttpsURLConnection con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-summoner/v2/summoners/puuid/" + key);
        JsonObject data = ConnectionManager.getResponseBodyAsJsonObject(con);
        if (!data.has("errorCode")) return Optional.of(data);
        return Optional.empty();
    }
}
