package com.iambadatplaying.restServlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ReworkedChampSelectData;
import com.iambadatplaying.lcuHandler.ConnectionManager;

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
        JsonObject json = getJsonObjectFromRequestBody(req);

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

    private void handlePick(HttpServletResponse response, JsonObject json) {
        if (json == null || json.isEmpty()) return;
        try {
            Integer championId = json.get("championId").getAsInt();
            boolean isLockIn = json.get("lockIn").getAsBoolean();
            int responseCode = pickChampion(championId, isLockIn);
            if (responseCode == -1) responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            response.setStatus(responseCode);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private void handleBan(HttpServletResponse response, JsonObject json) {
        if (json == null || json.isEmpty()) return;
        try {
            Integer championId = json.get("championId").getAsInt();
            boolean isLockIn = json.get("lockIn").getAsBoolean();
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
        Optional<JsonObject> optCurrentState = mainInitiator.getReworkedDataManager().getStateManagers(ReworkedChampSelectData.class).getCurrentState();
        if (!optCurrentState.isPresent()) return -1;
        JsonObject currentChampSelectState = optCurrentState.get();
        if (currentChampSelectState == null || currentChampSelectState.isEmpty() || championId == null) {
            return -1;
        }

        if (!currentChampSelectState.has("localPlayerCellId")) {
            return -1;
        }

        Integer localPlayerCellId = currentChampSelectState.get("localPlayerCellId").getAsInt();

        Optional<JsonArray> optMyTeam = Util.getOptJSONArray(currentChampSelectState, "myTeam");
        if (!optMyTeam.isPresent()) return -1;
        JsonArray myTeam = optMyTeam.get();

        Optional<JsonObject> optMe = Optional.empty();
        for (int i = 0, arrayLength = myTeam.size(); i< arrayLength; i++) {
            JsonObject player = myTeam.get(i).getAsJsonObject();
            if (player.isEmpty()) continue;
            if (!Util.jsonKeysPresent(player, "cellId", "championId")) continue;
            if (player.get("cellId").getAsInt() == localPlayerCellId) {
                optMe = Optional.of(player);
                break;
            }
        }

        if (!optMe.isPresent()) return -1;
        JsonObject me = optMe.get();

        Optional<JsonObject> specificActions = Optional.empty();
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
        JsonObject actions = specificActions.get();


        if (!Util.jsonKeysPresent(actions, "id")) return -1;
        int actionId = actions.get("id").getAsInt();
        JsonObject hoverAction = new JsonObject();
        hoverAction.addProperty("championId", championId);

        if (lockIn) {
            hoverAction.addProperty("completed", true);
        }

        String request = "/lol-champ-select/v1/session/actions/" + actionId;
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH, request, hoverAction.toString());
        return con.getResponseCode();
    }
}
