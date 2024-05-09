package com.iambadatplaying.data.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;

import java.util.Optional;

public class FriendHovercard extends MapDataManager<String> {

    protected FriendHovercard(Starter starter) {
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
        return null;
    }
}
