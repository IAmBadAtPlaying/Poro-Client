package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.map.RegaliaManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import org.json.JSONObject;

import java.util.Optional;

public class ChatMeManager extends StateDataManager {

    private static final String UPDATE_TYPE_SELF_PRESENCE = "SelfPresenceUpdate";

    private static final String lolChatV1MePattern = "/lol-chat/v1/me";

    public ChatMeManager(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return lolChatV1MePattern.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        switch (type) {
            case "Delete":
                resetState();
                break;
            case "Create":
            case "Update":
                Optional<JSONObject> updatedFEData = backendToFrontendChatMe(data);
                if (!updatedFEData.isPresent()) return;
                JSONObject updatedState = updatedFEData.get();
                if (updatedState.similar(currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
        }
    }


    private Optional<JSONObject> backendToFrontendChatMe(JSONObject data) {
        JSONObject frontendData = new JSONObject();

        if (!Util.jsonKeysPresent(data,"availability", "name", "icon")) return Optional.empty();
        Util.copyJsonAttributes(data, frontendData, "availability", "statusMessage", "name", "icon", "gameName", "gameTag", "pid" , "id", "puuid", "lol", "summonerId");

        frontendData.put("regalia", mainInitiator.getReworkedDataManager().getMapManagers(RegaliaManager.class.getSimpleName()).get(data.getBigInteger("summonerId")));

        return Optional.of(frontendData);
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JSONObject> fetchCurrentState() {
        if (currentState != null) return Optional.of(currentState);
        JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, lolChatV1MePattern));
        return backendToFrontendChatMe(data);
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString(UPDATE_TYPE_SELF_PRESENCE, currentState));
    }
}
