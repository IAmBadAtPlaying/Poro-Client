package com.iambadatplaying.data.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.MainInitiator;

public class FriendHovercard extends MapDataManager<String> {

    protected FriendHovercard(MainInitiator mainInitiator) {
        super(mainInitiator);
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
    public JsonObject load(String key) {
        return null;
    }
}
