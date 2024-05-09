package com.iambadatplaying.rest.jerseyServlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/quickplay/profiles")
public class QuickPlayProfileServlet {

    private static final int QUICKPLAY_SLOTS = 2;

    private static final int PERK_PRIMARY_SLOTS = 4;
    private static final int PERK_SUBSTYLE_SLOTS = 2;
    private static final int PERK_STAT_MOD_SLOTS = 3;


    @POST
    @Path("/{profileId}/active")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setActiveProfile(@PathParam("profileId") String profileId) {

        //TODO: Send LCU Settings active profile
        //TODO: If in lobby, update player slots

        return Response
                .status(Response.Status.NOT_IMPLEMENTED)
                .entity(ServletUtils.createErrorJson("Not implemented", "This feature is not implemented yet"))
                .build();
    }

    @GET
    @Path("/{profileId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfileById(@PathParam("profileId") String profileId) {
        return Response
                .status(Response.Status.NOT_IMPLEMENTED)
                .entity(ServletUtils.createErrorJson("Not implemented", "This feature is not implemented yet"))
                .build();
    }

    @DELETE
    @Path("/{profileId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteProfile(@PathParam("profileId") String profileId) {
        return Response
                .status(Response.Status.NOT_IMPLEMENTED)
                .entity(ServletUtils.createErrorJson("Not implemented", "This feature is not implemented yet"))
                .build();
    }

    @PATCH
    @Path("/{profileId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateProfile(@PathParam("profileId") String profileId, JsonElement profileJson) {
        return Response
                .status(Response.Status.NOT_IMPLEMENTED)
                .entity(ServletUtils.createErrorJson("Not implemented", "This feature is not implemented yet"))
                .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfiles() {
        return Response
                .status(Response.Status.NOT_IMPLEMENTED)
                .entity(ServletUtils.createErrorJson("Not implemented", "This feature is not implemented yet"))
                .build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createProfile(JsonElement profileJson) {
        if (profileJson == null) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "No JSON provided"))
                    .build();
        }

        if (!profileJson.isJsonObject()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "Not a JSON object"))
                    .build();
        }

        JsonObject jsonObject = profileJson.getAsJsonObject();

        if (!Util.jsonKeysPresent(jsonObject,"schemaVersion", "name","slots")) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "Missing keys"))
                    .build();
        }

        int schemaVersion = jsonObject.get("schemaVersion").getAsInt();
        String name = jsonObject.get("name").getAsString();
        JsonArray slots = jsonObject.get("slots").getAsJsonArray();

        if (slots.size() != QUICKPLAY_SLOTS) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "Invalid number of slots"))
                    .build();
        }

        for (JsonElement element : slots) {
            if (!element.isJsonObject()) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(ServletUtils.createErrorJson("Invalid JSON", "Slot is not a JSON object"))
                        .build();
            }

            JsonObject slot = element.getAsJsonObject();
            if (!Util.jsonKeysPresent(slot, "championId", "perks", "positionPreference", "skinId", "spell1", "spell2")) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(ServletUtils.createErrorJson("Invalid JSON", "Missing keys in slot"))
                        .build();
            }

            int championId = slot.get("championId").getAsInt();
            JsonObject perks = slot.get("perks").getAsJsonObject();
            String positionPreference = slot.get("positionPreference").getAsString();
            int skinId = slot.get("skinId").getAsInt();
            int spell1 = slot.get("spell1").getAsInt();
            int spell2 = slot.get("spell2").getAsInt();

            if (!Util.jsonKeysPresent(perks, "perkIds", "perkStyle", "perkSubStyle")) {
                return Response.
                        status(Response.Status.BAD_REQUEST)
                        .entity(ServletUtils.createErrorJson("Invalid JSON", "Missing keys in perks"))
                        .build();
            }

            JsonArray perkIds = perks.get("perkIds").getAsJsonArray();
            if (perkIds.size() != PERK_PRIMARY_SLOTS + PERK_SUBSTYLE_SLOTS + PERK_STAT_MOD_SLOTS) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(ServletUtils.createErrorJson("Invalid JSON", "Invalid number of perk slots"))
                        .build();
            }
        }

        UUID profileId = UUID.randomUUID();
        jsonObject.addProperty("profileId", profileId.toString());

        //TODO: Add profile to map

        Starter starter = Starter.getInstance();

//        starter.getConfigLoader().addProfile(profileId.toString(), jsonObject);

        return Response
                .status(Response.Status.CREATED)
                .entity(jsonObject)
                .build();
    }

}
