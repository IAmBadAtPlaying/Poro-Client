package com.iambadatplaying.config.quickplayProfiles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.config.ConfigModule;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.UUID;

@Path("/")
public class QuickPlayServlet {

    @GET
    @Path("/profiles")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getQuickPlayProfiles() {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(QuickPlayModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        QuickPlayModule quickModule = (QuickPlayModule) configModule;

        JsonObject profiles = new JsonObject();
        quickModule.getQuickplayProfiles().forEach(profiles::add);

        return Response
                .status(Response.Status.OK)
                .entity(profiles.toString())
                .build();
    }

    @POST
    @Path("/profiles")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addQuickPlayProfile(JsonElement element) {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(QuickPlayModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        QuickPlayModule quickModule = (QuickPlayModule) configModule;

        Optional<JsonObject> newProfileOptional = parseQuickPlayProfile(element);

        if (!newProfileOptional.isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        JsonObject newProfile = newProfileOptional.get();

        String profileId = UUID.randomUUID().toString();

        quickModule.getQuickplayProfiles().put(profileId, newProfile);


        JsonObject response = new JsonObject();
        response.add(profileId, newProfile);

        return Response
                .status(Response.Status.CREATED)
                .entity(response)
                .build();
    }

    @GET
    @Path("/profiles/{profileId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getQuickPlayProfile(@PathParam("profileId") String profileId) {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(QuickPlayModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        QuickPlayModule quickModule = (QuickPlayModule) configModule;

        if (!quickModule.getQuickplayProfiles().containsKey(profileId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        JsonObject response = new JsonObject();
        response.add(profileId, quickModule.getQuickplayProfiles().get(profileId));

        return Response
                .status(Response.Status.OK)
                .entity(response)
                .build();
    }

    @PUT
    @Path("/profiles/{profileId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateQuickPlayProfile(@PathParam("profileId") String profileId, JsonElement element) {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(QuickPlayModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        QuickPlayModule quickModule = (QuickPlayModule) configModule;

        if (!quickModule.getQuickplayProfiles().containsKey(profileId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<JsonObject> newProfileOptional = parseQuickPlayProfile(element);

        if (!newProfileOptional.isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        JsonObject newProfile = newProfileOptional.get();

        quickModule.getQuickplayProfiles().put(profileId, newProfile);

        JsonObject response = new JsonObject();
        response.add(profileId, newProfile);

        return Response
                .status(Response.Status.OK)
                .entity(response)
                .build();
    }

    @DELETE
    @Path("/profiles/{profileId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteQuickPlayProfile(@PathParam("profileId") String profileId) {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(QuickPlayModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        QuickPlayModule quickModule = (QuickPlayModule) configModule;

        if (!quickModule.getQuickplayProfiles().containsKey(profileId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        quickModule.getQuickplayProfiles().remove(profileId);

        return Response
                .status(Response.Status.NO_CONTENT)
                .build();
    }

    @POST
    @Path("/profiles/current/save")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveCurrentActiveProfile() {
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @POST
    @Path("/profiles/{profileId}/activate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response activateQuickPlayProfile(@PathParam("profileId") String profileId) {
        Starter starter = Starter.getInstance();
        //For this we will need an active connection to the LCU
        if (!starter.isInitialized()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }

        ConfigModule configModule = starter.getConfigLoader().getConfigModule(QuickPlayModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        QuickPlayModule quickModule = (QuickPlayModule) configModule;

        if (!quickModule.getQuickplayProfiles().containsKey(profileId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        JsonElement profile = quickModule.getQuickplayProfiles().get(profileId);

        //Save slots to settings
        Optional<JsonElement> lcuSettings = transformSettingsLCUProfile(profile.getAsJsonObject());
        lcuSettings.ifPresent(
                lcuJson -> {
                    HttpsURLConnection connection = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT, "/lol-settings/v2/account/LCUPreferences/lol-quick-play", lcuJson.toString());
                    try {
                        int respCode = connection.getResponseCode();
                        connection.disconnect();
                    } catch (Exception ignored) {}
                }
        );

        //Check if user is in lobby and if yes if quickplayProfiles are required
        JsonObject lobbyResp = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-lobby/v2/lobby"));
        if (lobbyResp != null && lobbyResp.has("gameConfig")) {
            JsonObject gameConfig = lobbyResp.getAsJsonObject("gameConfig");
            if (gameConfig.has("showQuickPlaySlotSelection") && gameConfig.get("showQuickPlaySlotSelection").getAsBoolean()) {
                //Activate quickplay profile
                Optional<JsonElement> lcuProfile = transformLobbyLCUProfile(profile.getAsJsonObject());
                lcuProfile.ifPresent(
                        lcuJson -> {
                            HttpsURLConnection connection = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT, "/lol-lobby/v1/lobby/members/localMember/player-slots", lcuJson.toString());
                            try {
                                int respCode = connection.getResponseCode();
                                connection.disconnect();
                            } catch (Exception ignored) {}
                        }
                );

            }
        }

        return Response
                .status(Response.Status.OK)
                .build();
    }


    private Optional<JsonObject> parseQuickPlayProfile(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject recvProfile = element.getAsJsonObject();
        JsonObject newProfile = new JsonObject();

        if (!Util.jsonKeysPresent(recvProfile, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_NAME, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOTS)) {
            return Optional.empty();
        }

        JsonElement recvSlots = recvProfile.get(QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOTS);

        if (!recvSlots.isJsonArray()) {
            return Optional.empty();
        }

        JsonArray slots = recvSlots.getAsJsonArray();
        JsonArray newSlots = new JsonArray();

        if (slots.size() != QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_AMOUNT) {
            return Optional.empty();
        }

        for (JsonElement slot : slots) {
            if (!slot.isJsonObject()) {
                return Optional.empty();
            }

            JsonObject recvSlot = slot.getAsJsonObject();
            JsonObject newSlot = new JsonObject();

            if (!Util.jsonKeysPresent(recvSlot, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_CHAMPION_ID, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_POSITION_PREFERENCE, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SKIN_ID, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL1, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL2)) {
                return Optional.empty();
            }

            JsonElement recvPerks = recvSlot.get(QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS);

            if (!recvPerks.isJsonObject()) {
                return Optional.empty();
            }

            JsonObject recvPerksObject = recvPerks.getAsJsonObject();
            JsonObject newPerks = new JsonObject();

            if (!Util.jsonKeysPresent(recvPerksObject,
                    QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_IDS,
                    QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_STYLE,
                    QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_SUBSTYLE
            )) {
                return Optional.empty();
            }

            Util.copyJsonAttributes(recvPerksObject, newPerks, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_IDS, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_STYLE, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_SUBSTYLE);

            Util.copyJsonAttributes(recvSlot, newSlot, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_CHAMPION_ID, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_POSITION_PREFERENCE, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SKIN_ID, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL1, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL2);
            newSlot.add(QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS, newPerks);

            newSlots.add(newSlot);
        }

        Util.copyJsonAttributes(recvProfile, newProfile, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_NAME);
        newProfile.add(QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOTS, newSlots);

        return Optional.of(newProfile);
    }


    private Optional<JsonElement> transformLobbyLCUProfile(JsonObject profile) {
        JsonArray lcuProfile = new JsonArray();

        JsonArray slots = profile.get(QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOTS).getAsJsonArray();

        for (JsonElement backendSlot: slots) {
            JsonObject lcuSlot = new JsonObject();
            JsonObject backendSlotObj = backendSlot.getAsJsonObject();

            Util.copyJsonAttributes(backendSlotObj, lcuSlot, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_CHAMPION_ID, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_POSITION_PREFERENCE, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SKIN_ID, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL1, QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL2);

            JsonObject backendPerks = backendSlotObj.get(QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS).getAsJsonObject();

            lcuSlot.addProperty(QuickPlayModule.PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS, backendPerks.toString());

            lcuProfile.add(lcuSlot);
        }

        return Optional.of(lcuProfile);
    }

    private Optional<JsonElement> transformSettingsLCUProfile(JsonObject profile) {
        final int SETTINGS_SCHEMA_VERSION = 1;
        final String SETTINGS_KEY_SCHEMA_VERSION = "schemaVersion";

        JsonObject lcuProfile = new JsonObject();

        lcuProfile.addProperty(SETTINGS_KEY_SCHEMA_VERSION, SETTINGS_SCHEMA_VERSION);

        Optional<JsonElement> lcuSlots = transformLobbyLCUProfile(profile);

        if (!lcuSlots.isPresent()) {
            return Optional.empty();
        }

        JsonObject data = new JsonObject();
        data.add("slots", lcuSlots.get());

        lcuProfile.add("data", data);
        return Optional.of(lcuProfile);
    }
}
