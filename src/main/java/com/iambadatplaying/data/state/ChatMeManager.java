package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.data.map.RegaliaManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import java.util.Optional;

public class ChatMeManager extends StateDataManager {

    private static final String UPDATE_TYPE_SELF_PRESENCE = ReworkedDataManager.UPDATE_TYPE_SELF_PRESENCE;

    private static final String lolChatV1MePattern = "/lol-chat/v1/me";

    public ChatMeManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return lolChatV1MePattern.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
                resetState();
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                if (!data.isJsonObject()) return;
                Optional<JsonObject> updatedFEData = backendToFrontendChatMe(data.getAsJsonObject());
                if (!updatedFEData.isPresent()) return;
                JsonObject updatedState = updatedFEData.get();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }


    private Optional<JsonObject> backendToFrontendChatMe(JsonObject data) {
        JsonObject frontendData = new JsonObject();

        if (!Util.jsonKeysPresent(data, "availability", "name", "icon")) return Optional.empty();
        Util.copyJsonAttributes(data, frontendData, "availability", "statusMessage", "name", "icon", "gameName", "gameTag", "pid", "id", "puuid", "lol", "summonerId");

        starter.getReworkedDataManager()
                .getMapManagers(RegaliaManager.class)
                .get(data.get("summonerId").getAsBigInteger())
                .ifPresent(
                        regalia -> frontendData.add("regalia", regalia)
                );

        return Optional.of(frontendData);
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        if (currentState != null) return Optional.of(currentState);
        JsonObject data = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, lolChatV1MePattern));
        return backendToFrontendChatMe(data);
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(UPDATE_TYPE_SELF_PRESENCE, currentState));
    }

    @Override
    public String getEventName() {
        return ReworkedDataManager.UPDATE_TYPE_SELF_PRESENCE;
    }
}
