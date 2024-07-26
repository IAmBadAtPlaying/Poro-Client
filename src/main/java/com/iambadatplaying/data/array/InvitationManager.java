package com.iambadatplaying.data.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.DataManager;
import com.iambadatplaying.data.map.GameNameManager;
import com.iambadatplaying.data.map.SummonerIdToPuuidManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InvitationManager extends ArrayDataManager {

    private static final Pattern INVITATION_PATTERN = Pattern.compile("/lol-lobby/v2/received-invitations$");
    private List<String> invitations = null;

    public InvitationManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {
        invitations = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return INVITATION_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
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
                if (Util.equalJsonElements(updatedState, currentArray)) return;
                currentArray = updatedState;
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
        if (currentArray != null) return Optional.of(currentArray);
        JsonArray data = ConnectionManager.getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-lobby/v2/received-invitations"));
        log("Got data " + data, Starter.LOG_LEVEL.DEBUG);
        if (data == null) return Optional.empty();
        return backendToFrontendInvitations(data);
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
            final JsonObject frontendObj = new JsonObject();
            Util.copyJsonAttributes(obj, frontendObj, "canAcceptInvitation", "invitationId", "invitationType", "gameConfig", "fromSummonerName");
            newInvitations.add(obj.get("invitationId").getAsString());
            log("Looking for puuidObj with summonerId " + obj.get("fromSummonerId").getAsBigInteger(), Starter.LOG_LEVEL.DEBUG);
            starter.getDataManager()
                            .getMapManager(SummonerIdToPuuidManager.class)
                                    .get(obj.get("fromSummonerId").getAsBigInteger())
                                            .ifPresent(
                                                    puuidObj -> {
                                                        log("Got puuidObj " + puuidObj, Starter.LOG_LEVEL.DEBUG);
                                                        starter.getDataManager()
                                                                .getMapManager(GameNameManager.class)
                                                                .get(puuidObj.get(SummonerIdToPuuidManager.KEY_PUUID).getAsString())
                                                                .ifPresent(
                                                                        gameName -> {
                                                                            frontendObj.addProperty("fromGameName", gameName.get("gameName").getAsString());
                                                                            frontendObj.addProperty("fromTagLine", gameName.get("tagLine").getAsString());
                                                                        }
                                                                );
                                                    }
                                            );
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
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(getEventName(), currentArray));
    }

    @Override
    public String getEventName() {
        return DataManager.UPDATE_TYPE_INVITATIONS;
    }
}
