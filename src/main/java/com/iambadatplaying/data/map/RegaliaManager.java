package com.iambadatplaying.data.map;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ChatMeManager;
import com.iambadatplaying.data.state.LobbyData;
import com.iambadatplaying.data.state.StateDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

public class RegaliaManager extends MapDataManager<BigInteger>{

    private final static String lolRegaliaV2SummonerPattern = "/lol-regalia/v2/summoners/(.*)/regalia/async";

    public RegaliaManager(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    @Override
    public JSONObject load(BigInteger key) {
        return updateRegalia(key);
    }

    @Override
    public void doInitialize() {
    }

    protected boolean isRelevantURI(String uri) {
        return uri.matches(lolRegaliaV2SummonerPattern);
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        switch (type) {
            case "Delete":
                break;
            case "Create":
            case "Update":
                String summonerIdStr = uri.replaceAll(lolRegaliaV2SummonerPattern, "$1");
                BigInteger summonerId = new BigInteger(summonerIdStr);
                updateRegalia(summonerId);
                break;
        }
    }

    @Override
    public void doShutdown() {
        map.clear();
        map = null;
    }

    public void update(String uri, String type, JSONObject data) {
        if (!isRelevantURI(uri)) return;
        switch (type) {
            case "Delete":
                break;
            case "Create":
            case "Update":
                String summonerIdStr = uri.replaceAll(DataManager.REGALIA_REGEX, "$1");

                summonerIdStr  = summonerIdStr .replaceAll("^/+", "").replaceAll("/+$", "");;
                BigInteger summonerId = new BigInteger(summonerIdStr);
                updateRegalia(summonerId);
                break;
        }
    }


    public JSONObject getRegalia(BigInteger summonerId) {
        if (map.containsKey(summonerId)) {
            return map.get(summonerId);
        } else {
            return updateRegalia(summonerId);
        }
    }

    private void updateChatMe(BigInteger summonerId) {
        StateDataManager chatMeManager = mainInitiator.getReworkedDataManager().getStateManagers(ChatMeManager.class.getSimpleName());
        if (chatMeManager == null) return;
        Optional<JSONObject> optChatMeData = chatMeManager.getCurrentState();
        if (!optChatMeData.isPresent()) return;
        JSONObject chatMeData = optChatMeData.get();

        if (!chatMeData.has("summonerId")) return;
        if (!summonerId.equals(chatMeData.getBigInteger("summonerId"))) return;

        chatMeData.put("regalia", getRegalia(summonerId));
        mainInitiator.getReworkedDataManager().getStateManagers(ChatMeManager.class.getSimpleName()).setCurrentState(chatMeData);
        mainInitiator.getReworkedDataManager().getStateManagers(ChatMeManager.class.getSimpleName()).sendCurrentState();
    }

    private void updateLobbyMemeberRegalia(BigInteger summonerId) {
        StateDataManager lobbyManager = mainInitiator.getReworkedDataManager().getStateManagers(LobbyData.class.getSimpleName());
        if (lobbyManager == null) return;
        Optional<JSONObject> optLobbyData = lobbyManager.getCurrentState();
        if (!optLobbyData.isPresent()) return;
        JSONObject lobbyData = optLobbyData.get();

        Optional<JSONArray> optMembers = Util.getJSONArray(lobbyData, "members");
        if (!optMembers.isPresent()) return;
        JSONArray members = optMembers.get();

        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.getJSONObject(i);
            if (member != null && !member.isEmpty() && member.has("summonerId")) {
                if (!summonerId.equals(member.getBigInteger("summonerId"))) continue;
                member.put("regalia", getRegalia(summonerId));
                break;
            }
        }

        lobbyData.put("members", members);

        mainInitiator.getReworkedDataManager().getStateManagers(LobbyData.class.getSimpleName()).setCurrentState(lobbyData);
        mainInitiator.getReworkedDataManager().getStateManagers(LobbyData.class.getSimpleName()).sendCurrentState();
    }

    public JSONObject updateRegalia(BigInteger summonerId) {
        JSONObject regalia = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-regalia/v3/summoners/"+summonerId.toString()+"/regalia"));
        map.put(summonerId, regalia);
        updateChatMe(summonerId);
        updateLobbyMemeberRegalia(summonerId);
        return regalia;
    }
}
