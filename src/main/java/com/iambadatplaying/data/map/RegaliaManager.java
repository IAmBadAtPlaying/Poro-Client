package com.iambadatplaying.data.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ChatMeManager;
import com.iambadatplaying.data.state.LobbyData;
import com.iambadatplaying.data.state.StateDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegaliaManager extends MapDataManager<BigInteger> {

    private final static Pattern lolRegaliaV2SummonerPattern = Pattern.compile("/lol-regalia/v2/summoners/(.*)/regalia/async");

    public RegaliaManager(Starter starter) {
        super(starter);
    }

    @Override
    public Optional<JsonObject> load(BigInteger key) {
        return Optional.ofNullable(updateRegalia(key));
    }

    @Override
    public void doInitialize() {
    }

    protected Matcher getURIMatcher(String uri) {
        return lolRegaliaV2SummonerPattern.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        String summonerIdStr = uriMatcher.replaceAll("$1");
        BigInteger summonerId = new BigInteger(summonerIdStr);
        switch (type) {
            case UPDATE_TYPE_DELETE:
                map.remove(summonerId);
                break;
            case UPDATE_TYPE_CREATE:
            case UPDATE_TYPE_UPDATE:
                updateRegalia(summonerId);
                break;
        }
    }

    @Override
    public void doShutdown() {
    }

    public JsonObject getRegalia(BigInteger summonerId) {
        if (map.containsKey(summonerId)) {
            return map.get(summonerId);
        } else {
            return updateRegalia(summonerId);
        }
    }

    private void updateChatMe(BigInteger summonerId) {
        StateDataManager chatMeManager = starter.getReworkedDataManager().getStateManagers(ChatMeManager.class);
        if (chatMeManager == null) return;
        Optional<JsonObject> optChatMeData = chatMeManager.getCurrentState();
        if (!optChatMeData.isPresent()) return;
        JsonObject chatMeData = optChatMeData.get();

        if (!chatMeData.has("summonerId")) return;
        if (!summonerId.equals(chatMeData.get("summonerId").getAsBigInteger())) return;

        chatMeData.add("regalia", getRegalia(summonerId));
        starter.getReworkedDataManager().getStateManagers(ChatMeManager.class).setCurrentState(chatMeData);
        starter.getReworkedDataManager().getStateManagers(ChatMeManager.class).sendCurrentState();
    }

    private void updateLobbyMemeberRegalia(BigInteger summonerId) {
        StateDataManager lobbyManager = starter.getReworkedDataManager().getStateManagers(LobbyData.class);
        if (lobbyManager == null) return;
        Optional<JsonObject> optLobbyData = lobbyManager.getCurrentState();
        if (!optLobbyData.isPresent()) return;
        JsonObject lobbyData = optLobbyData.get();

        Optional<JsonArray> optMembers = Util.getOptJSONArray(lobbyData, "members");
        if (!optMembers.isPresent()) return;
        JsonArray members = optMembers.get();

        for (int i = 0; i < members.size(); i++) {
            JsonObject member = members.get(i).getAsJsonObject();
            if (member != null && !member.isEmpty() && member.has("summonerId")) {
                if (!summonerId.equals(member.get("summonerId").getAsBigInteger())) continue;
                member.add("regalia", getRegalia(summonerId));
                break;
            }
        }

        lobbyData.add("members", members);

        starter.getReworkedDataManager().getStateManagers(LobbyData.class).setCurrentState(lobbyData);
        starter.getReworkedDataManager().getStateManagers(LobbyData.class).sendCurrentState();
    }

    public JsonObject updateRegalia(BigInteger summonerId) {
        JsonObject regalia = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-regalia/v3/summoners/" + summonerId.toString() + "/regalia"));
        map.put(summonerId, regalia);
        updateChatMe(summonerId);
        updateLobbyMemeberRegalia(summonerId);
        return regalia;
    }

}
