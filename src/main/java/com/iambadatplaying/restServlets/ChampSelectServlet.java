package com.iambadatplaying.restServlets;

import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ChampSelectData;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import static com.iambadatplaying.data.state.ChampSelectData.INSTRUCTION_PLAY_SOUND;

public class ChampSelectServlet extends BaseRESTServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String actionType = getActionTypeFromPath(req.getPathInfo());
        if (actionType == null || actionType.isEmpty()) return;
        JSONObject json = getJsonFromRequestBody(req);

        switch (actionType) {
            case "pick":
                handlePick(resp, json);
                break;
            case "ban":
                handleBan(resp, json);
                break;
            default:
                break;
        }
    }

    private String getActionTypeFromPath(String pathInfo) {
        if (pathInfo != null && pathInfo.length() > 1) {
            String[] pathParts = pathInfo.split("/");
            String actionType = pathParts[pathParts.length - 1];
            if (actionType == null) return null;
            return actionType.toLowerCase();
        }
        return null;
    }

    private void handlePick(HttpServletResponse response, JSONObject json) {
        if (json == null || json.isEmpty()) return;
        try {
            Integer championId = json.getInt("championId");
            boolean isLockIn = json.getBoolean("lockIn");
            int responseCode = pickChampion(championId, isLockIn);
            if (responseCode == -1) responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            response.setStatus(responseCode);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private void handleBan(HttpServletResponse response, JSONObject json) {
        if (json == null || json.isEmpty()) return;
        try {
            Integer championId = json.getInt("championId");
            boolean isLockIn = json.getBoolean("lockIn");
            int responseCode = banChampion(championId, isLockIn);
            if (responseCode == -1) responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            response.setStatus(responseCode);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    public int pickChampion (Integer championId,boolean lockIn) throws IOException {
        return performChampionAction("pick", championId, lockIn);
    }

    public int banChampion (Integer championId,boolean lockIn) throws IOException {
        return performChampionAction("ban", championId, lockIn);
    }


    private int performChampionAction ( String actionType, Integer championId, boolean lockIn) throws IOException {
        Optional<JSONObject> optCurrentState = mainInitiator.getReworkedDataManager().getStateManagers(ChampSelectData.class.getSimpleName()).getCurrentState();
        if (!optCurrentState.isPresent()) return -1;
        JSONObject currentChampSelectState = optCurrentState.get();
        if (currentChampSelectState == null || currentChampSelectState.isEmpty() || championId == null) {
            return -1;
        }

        if (!currentChampSelectState.has("localPlayerCellId")) {
            return -1;
        }

        Integer localPlayerCellId = currentChampSelectState.getInt("localPlayerCellId");

        Optional<JSONArray> optActions = Util.getJSONArray(currentChampSelectState, "actions");
        if (!optActions.isPresent()) return -1;
        JSONArray actions = optActions.get();

        JSONObject relevantAction = null;
        for (int i = 0; i < actions.length(); i++) {
            JSONArray subAction = actions.getJSONArray(i);
            if (subAction == null || subAction.isEmpty()) continue;
            for (int j = 0; j < subAction.length(); j++) {
                JSONObject singleAction = subAction.getJSONObject(j);
                if (singleAction == null || singleAction.isEmpty()) continue;
                if (!Util.jsonKeysPresent(singleAction, "actorCellId", "completed", "type", "isInProgress")) continue;
                if (singleAction.getInt("actorCellId") != localPlayerCellId) continue;
                if (!singleAction.getBoolean("isInProgress")) continue;
                if (!singleAction.getString("type").equals(actionType)) continue;
                relevantAction = singleAction;
                break;
            }
        }


        if (relevantAction == null || relevantAction.isEmpty()) {
            return -1;
        }

        int actionId = relevantAction.getInt("id");
        JSONObject hoverAction = new JSONObject();
        hoverAction.put("championId", championId);

        if (lockIn) {
            hoverAction.put("completed", true);
        }

        String request = "/lol-champ-select/v1/session/actions/" + actionId;
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH, request, hoverAction.toString());
        return con.getResponseCode();
    }
}
