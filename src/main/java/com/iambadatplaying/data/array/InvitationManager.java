package com.iambadatplaying.data.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.util.*;

public class InvitationManager extends ArrayDataManager {

    private static final String INVITATION_URI = "/lol-lobby/v2/received-invitations";

    public InvitationManager(Starter starter) {
        super(starter);
    }

    private List<String> invitations = null;

    @Override
    protected void doInitialize() {
        invitations = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return INVITATION_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case UPDATE_TYPE_DELETE:
                resetState();
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                if (!data.isJsonArray()) return;
                Optional<JsonArray> updatedFEData = backendToFrontendInvitations(data.getAsJsonArray());
                if (!updatedFEData.isPresent()) return;
                JsonArray updatedState = updatedFEData.get();
                if (Util.equalJsonElements(updatedState, array)) return;
                array = updatedState;
                sendCurrentState();
                break;
        }
    }

    @Override
    protected void doShutdown() {
        invitations.clear();
        invitations = null;
    }

    @Override
    protected Optional<JsonArray> fetchCurrentState() {
        if (array != null) return Optional.of(array);
        JsonArray data = ConnectionManager.getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-lobby/v2/received-invitations"));
        return Optional.of(data);
    }

    private Optional<JsonArray> backendToFrontendInvitations(JsonArray data) {
        ArrayList<String> newInvitations = new ArrayList<>();
        JsonArray frontendData = new JsonArray();
        if (data == null) return Optional.empty();
        for (JsonElement element : data) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            if (!Util.jsonKeysPresent(obj, "canAcceptInvitation", "invitationId", "invitationType", "gameConfig")) {
                continue;
            }
            JsonObject frontendObj = new JsonObject();
            Util.copyJsonAttributes(obj, frontendObj, "canAcceptInvitation", "invitationId", "invitationType", "gameConfig", "fromSummonerName");
            newInvitations.add(obj.get("invitationId").getAsString());
            frontendData.add(frontendObj);
        }

        //Remove old invitations that are not present in the new data
        Iterator<String> iterator = invitations.iterator();
        while (iterator.hasNext()) {
            String invitation = iterator.next();
            if (!newInvitations.contains(invitation)) {
                iterator.remove();
                log("Removed invitation " + invitation, Starter.LOG_LEVEL.DEBUG);
            }
        }

        //Add new invitations that are not present in the old data
        for (String newInvitation : newInvitations) {
            if (!invitations.contains(newInvitation)) {
                log("Added invitation " + newInvitation, Starter.LOG_LEVEL.DEBUG);
                invitations.add(newInvitation);
            }
        }

        return Optional.of(frontendData);
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(getEventName(), array));
    }

    @Override
    public String getEventName() {
        return ReworkedDataManager.UPDATE_TYPE_INVITATIONS;
    }
}
