package com.iambadatplaying.restServlets;

import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ChampSelectData;
import com.iambadatplaying.data.state.ReworkedChampSelectData;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

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
        Optional<JSONObject> optCurrentState = mainInitiator.getReworkedDataManager().getStateManagers(ReworkedChampSelectData.class.getSimpleName()).getCurrentState();
        if (!optCurrentState.isPresent()) return -1;
        JSONObject currentChampSelectState = optCurrentState.get();
        if (currentChampSelectState == null || currentChampSelectState.isEmpty() || championId == null) {
            return -1;
        }

        if (!currentChampSelectState.has("localPlayerCellId")) {
            return -1;
        }

        Integer localPlayerCellId = currentChampSelectState.getInt("localPlayerCellId");

        Optional<JSONArray> optMyTeam = Util.getOptJSONArray(currentChampSelectState, "myTeam");
        if (!optMyTeam.isPresent()) return -1;
        JSONArray myTeam = optMyTeam.get();

        Optional<JSONObject> optMe = Optional.empty();
        for (int i = 0, arrayLength = myTeam.length(); i< arrayLength; i++) {
            JSONObject player = myTeam.getJSONObject(i);
            if (player.isEmpty()) continue;
            if (!Util.jsonKeysPresent(player, "cellId", "championId")) continue;
            if (player.getInt("cellId") == localPlayerCellId) {
                optMe = Optional.of(player);
                break;
            }
        }

        if (!optMe.isPresent()) return -1;
        JSONObject me = optMe.get();

        Optional<JSONObject> specificActions = Optional.empty();
        switch (actionType) {
            case "pick":
                    specificActions = Util.getOptJSONObject(me, "pickAction");
                break;
            case "ban":
                    specificActions = Util.getOptJSONObject(me, "banAction");
                break;
            default:
                return -1;
        }

        if (!specificActions.isPresent()) return -1;
        JSONObject actions = specificActions.get();


        int actionId = actions.getInt("id");
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
