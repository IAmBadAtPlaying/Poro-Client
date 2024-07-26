package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.state.ReworkedChampSelectData;
import com.iambadatplaying.data.state.StateDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;

@Path("/champ-select")
public class ChampSelectServlet {
    private static final String PICK = "pick";
    private static final String BAN = "ban";

    private static final int CHAMPION_NONE_ID = -1;

    @POST
    @Path("/donate-reroll")
    public Response donateReroll() {
        Starter starter = Starter.getInstance();
        StateDataManager manager = starter.getDataManager().getStateManager(ReworkedChampSelectData.class);
        if (manager == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }

        ReworkedChampSelectData reworkedChampSelectData = (ReworkedChampSelectData) manager;
        Optional<JsonObject> optCurrentState = reworkedChampSelectData.getCurrentState();
        if (!optCurrentState.isPresent()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }

        JsonObject currentChampSelectState = optCurrentState.get();
        if (!Util.jsonKeysPresent(currentChampSelectState, "benchChampions","benchEnabled","localPlayerCellId","rerollsRemaining","myTeam")) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        boolean benchEnabled = currentChampSelectState.get("benchEnabled").getAsBoolean();
        int rerollsRemaining = currentChampSelectState.get("rerollsRemaining").getAsInt();
        int localPlayerCellId = currentChampSelectState.get("localPlayerCellId").getAsInt();

        if (!benchEnabled) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Unable to donate reroll", "The bench is not enabled, maybe you are in a mode that does not support rerolls?"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        if (rerollsRemaining <= 0) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Unable to donate reroll", "You have no rerolls remaining"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        JsonArray myTeam = currentChampSelectState.getAsJsonArray("myTeam");
        Optional<JsonObject> optMe = myTeam
                .asList()
                .stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(player -> player.get("cellId").getAsInt() == localPlayerCellId)
                .findFirst();

        if (!optMe.isPresent()) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Unable to donate reroll", "Server logic error, could not find your player object"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        JsonObject me = optMe.get();
        if (!Util.jsonKeysPresent(me,  "championId")) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Unable to donate reroll", "Server logic error, could not find required data in your player object"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        int myChampionId = me.get("championId").getAsInt();

        ConnectionManager connectionManager = starter.getConnectionManager();
        HttpsURLConnection con = connectionManager.buildConnection(ConnectionManager.conOptions.POST, "/lol-champ-select/v1/session/my-selection/reroll");
        try {
            int responseCode = con.getResponseCode();
            if (responseCode != Response.Status.OK.getStatusCode() && responseCode != Response.Status.NO_CONTENT.getStatusCode()) {
                return Response
                        .status(responseCode)
                        .entity(ServletUtils.createErrorJson("Unable to donate reroll", "League client retured error for \"/lol-champ-select/v1/session/my-selection/reroll\""))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
            }

            HttpsURLConnection swapCon = connectionManager.buildConnection(ConnectionManager.conOptions.POST, "/lol-champ-select/v1/session/bench/swap/" + myChampionId);
            responseCode = swapCon.getResponseCode();

            if (responseCode != Response.Status.OK.getStatusCode() && responseCode != Response.Status.NO_CONTENT.getStatusCode()) {
                return Response
                        .status(responseCode)
                        .entity(ServletUtils.createErrorJson("Unable to donate reroll", "League client retured error for \"/lol-champ-select/v1/session/bench/swap/" + myChampionId + "\""))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build();
            }
        } catch (IOException e) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Unable to donate reroll", "Server error"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        return Response
                .status(Response.Status.OK)
                .build();
    }

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
        Optional<JsonObject> optCurrentState = Starter.getInstance().getDataManager()
                .getStateManager(ReworkedChampSelectData.class).getCurrentState();
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

        Optional<JsonObject> specificActions;
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
            hoverAction.addProperty("completed", true);
        }

        String requestPath = "/lol-champ-select/v1/session/actions/" + actionId;
        HttpsURLConnection con = Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH, requestPath, hoverAction.toString());
        log(ConnectionManager.handleStringResponse(con), Starter.LOG_LEVEL.DEBUG);
        try {
            return con.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private void log(Object o, Starter.LOG_LEVEL level) {
        Starter.getInstance().log(this.getClass().getSimpleName() + ": " + o, level);
    }

    private void log(Object o) {
        log(o, Starter.LOG_LEVEL.DEBUG);
    }
}
