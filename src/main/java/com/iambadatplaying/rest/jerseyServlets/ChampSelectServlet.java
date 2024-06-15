package com.iambadatplaying.rest.jerseyServlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;

@Path("/champ-select")
public class ChampSelectServlet {
    private static final String PICK = "pick";
    private static final String BAN = "ban";

    @POST
    @Path("/pick")
    public Response pickChampion(JsonElement jsonElement) {
        if (jsonElement == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!jsonElement.isJsonObject()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (!Util.jsonKeysPresent(jsonObject, "championId", "lockIn")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Boolean lockIn = jsonObject.get("lockIn").getAsBoolean();
        Integer championId = jsonObject.get("championId").getAsInt();

        int responseCode = performChampionAction(PICK, championId, lockIn);
        if (responseCode == -1) {
            responseCode = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        }

        return Response.status(responseCode).build();
    }

    @POST
    @Path("/ban")
    public Response banChampion(JsonElement jsonElement) {
        if (jsonElement == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!jsonElement.isJsonObject()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (!Util.jsonKeysPresent(jsonObject, "championId", "lockIn")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Boolean lockIn = jsonObject.get("lockIn").getAsBoolean();
        Integer championId = jsonObject.get("championId").getAsInt();

        int responseCode = performChampionAction(BAN, championId, lockIn);
        if (responseCode == -1) {
            responseCode = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        }

        return Response.status(responseCode).build();
    }


    private int performChampionAction(String actionType, Integer championId, Boolean lockIn) {
        Optional<JsonObject> optCurrentState = Starter.getInstance().getReworkedDataManager()
                .getStateManagers(ReworkedDataManager.class).getCurrentState();
        if (!optCurrentState.isPresent()) return -1;
        JsonObject currentChampSelectState = optCurrentState.get();
        if (currentChampSelectState.isEmpty() && !currentChampSelectState.has("localPlayerCellId")) {
            return -1;
        }

        Integer localPlayerCellId = currentChampSelectState.get("localPlayerCellId").getAsInt();

        Optional<JsonArray> optMyTeam = Util.getOptJSONArray(currentChampSelectState, "myTeam");
        if (!optMyTeam.isPresent()) return -1;
        JsonArray myTeam = optMyTeam.get();

        Optional<JsonObject> optMe = Optional.empty();
        for (int i = 0, arrayLength = myTeam.size(); i < arrayLength; i++) {
            JsonObject player = myTeam.get(i).getAsJsonObject();
            if (player.isEmpty() || !Util.jsonKeysPresent(player, "cellId", "championId")) continue;
            if (player.get("cellId").getAsInt() == localPlayerCellId) {
                optMe = Optional.of(player);
                break;
            }
        }

        if (!optMe.isPresent()) return -1;
        JsonObject me = optMe.get();

        Optional<JsonObject> specificActions = Optional.empty();
        switch (actionType) {
            case PICK:
                specificActions = Util.getOptJSONObject(me, "pickAction");
                break;
            case BAN:
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
            hoverAction.addProperty("lockIn", true);
        }

        String requestPath = "/lol-champ-select/v1/session/actions/" + actionId;
        HttpsURLConnection con = Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH, requestPath, hoverAction.toString());
        try {
            return con.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
