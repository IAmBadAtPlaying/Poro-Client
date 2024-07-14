package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigInteger;
import java.util.Optional;

@Path("/runes")
public class RunesServlet {

    @Path("/save")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveRunes(JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonObject()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "The message body could not be parsed to a valid JSON Element"))
                    .build();
        }

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        jsonObject.addProperty("current", true);

        if (!Util.jsonKeysPresent(jsonObject, "selectedPerkIds", "name", "primaryStyleId", "subStyleId")) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "Missing keys in JSON"))
                    .build();
        }


        BigInteger currentRunepageId = getCurrentRunepageId().orElse(getValidRunepageId().orElse(null));
        if (currentRunepageId == null) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("No valid runepage found", "No valid runepage found"))
                    .build();
        }

        deleteRunePage(currentRunepageId);

        createNewRunePage(jsonObject);

        return Response
                .status(Response.Status.OK)
                .entity(jsonObject)
                .build();
    }


    private Optional<BigInteger> getCurrentRunepageId() {
        HttpsURLConnection con = Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-perks/v1/currentpage");
        JsonObject response = ConnectionManager.getResponseBodyAsJsonObject(con);
        if (response == null) {
            return Optional.empty();
        }

        if (!Util.jsonKeysPresent(response, "id", "isTemporary")) {
            return Optional.empty();
        }

        if (response.get("isTemporary").getAsBoolean()) {
            return Optional.empty();
        }

        return Optional.of(response.get("id").getAsBigInteger());
    }

    private Optional<BigInteger> getValidRunepageId() {
        HttpsURLConnection con = Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-perks/v1/pages");
        JsonArray response = ConnectionManager.getResponseBodyAsJsonArray(con);
        if (response == null) {
            return Optional.empty();
        }

        if (response.isEmpty()) {
            return Optional.empty();
        }

        BigInteger lastValidId = null;
        for (int i = 0; i < response.size(); i++) {
            JsonObject page = response.get(i).getAsJsonObject();
            if (!page.get("isTemporary").getAsBoolean()) {
                lastValidId = page.get("id").getAsBigInteger();
                if (page.get("name").getAsString().startsWith("Poro-Client")) {
                    return Optional.of(page.get("id").getAsBigInteger());
                }
            }
        }

        return Optional.ofNullable(lastValidId);
    }

    private void deleteRunePage(BigInteger pageId) {
        if (pageId == null) {
            return;
        }

        Starter.getInstance().getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.DELETE, "/lol-perks/v1/pages/" + pageId));
    }

    private void createNewRunePage(JsonObject body) {
        if (body == null || body.isEmpty()) {
            return;
        }

        Starter.getInstance().getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-perks/v1/pages", body.toString()));
    }
}
