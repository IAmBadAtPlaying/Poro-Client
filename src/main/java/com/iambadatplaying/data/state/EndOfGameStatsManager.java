package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.ReworkedDataManager;

import java.util.Optional;

public class EndOfGameStatsManager extends StateDataManager {

    private static final String UPDATE_TYPE_END_OF_GAME_STATS = ReworkedDataManager.UPDATE_TYPE_STATS_EOG;

    private static final String endOfGameStatsUri = "/lol-end-of-game/v1/eog-stats-block";

    public EndOfGameStatsManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return endOfGameStatsUri.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {

    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {

    }

    @Override
    public String getEventName() {
        return null;
    }
}
