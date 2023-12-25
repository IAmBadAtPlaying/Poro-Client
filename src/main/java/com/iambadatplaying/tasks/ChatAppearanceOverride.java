package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;

public class ChatAppearanceOverride extends Task {

    private final static String lol_gameflow_v1_gameflow_phase = "/lol-gameflow/v1/session";

    private Integer iconId;
    private Integer challengePoints;
    private String rankedLeagueQueue;
    private String rankedLeagueTier;
    private String challengeCrystalLevel;
    private Integer masteryScore;

    private String availability;

    public void notify(JsonArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.size() < 3) {
            return;
        }
        JsonObject data = webSocketEvent.get(2).getAsJsonObject();
        String uriTrigger = data.get("uri").getAsString();
        if (lol_gameflow_v1_gameflow_phase.equals(uriTrigger)) {
            handleUpdateData(data);
        }
    }

    private JsonObject buildChatAppearanceOverride() {
        JsonObject chatAppearanceOverride = new JsonObject();
        try {
            JsonObject lol = new JsonObject();
            if (challengePoints != null) {
                lol.addProperty("challengePoints", challengePoints.toString());
            }
            if (masteryScore != null) {
                lol.addProperty("masteryScore", masteryScore.toString());
            }
            lol.addProperty("rankedLeagueQueue", rankedLeagueQueue);
            lol.addProperty("challengeCrystalLevel", challengeCrystalLevel);
            lol.addProperty("rankedLeagueTier", rankedLeagueTier);

            chatAppearanceOverride.add("lol", lol);

            if (iconId != null) {
                chatAppearanceOverride.addProperty("icon", iconId.intValue());
            }

            chatAppearanceOverride.addProperty("availability", availability);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mainInitiator.log(chatAppearanceOverride.toString());
        return chatAppearanceOverride;
    }

    private void handleUpdateData(JsonObject updateData) {
        if (updateData == null || updateData.isEmpty()) return;
        String newGameflowPhase = updateData.get("data").getAsString();
        if ("EndOfGame".equals(newGameflowPhase)) {
            JsonObject body = buildChatAppearanceOverride();
            sendChatAppearanceOverride(body);
        }
    }

    private void sendChatAppearanceOverride(JsonObject body) {
        try {
            HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT, "/lol-chat/v1/me", body.toString());
            String response = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, con);
            mainInitiator.log(response);
            con.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void doInitialize() {
        //Not needed
    }

    protected void doShutdown() {
        iconId = null;
        challengePoints = null;
        rankedLeagueQueue = null;
        rankedLeagueTier = null;
        challengeCrystalLevel = null;
        masteryScore = null;

        availability = null;
    }

    public boolean setTaskArgs(JsonObject arguments) {
        try {
            if (arguments.has("iconId")) {
                iconId = arguments.get("iconId").getAsInt();
            }

            if (arguments.has("challengePoints")) {
                challengePoints = arguments.get("challengePoints").getAsInt();
            }

            if (arguments.has("rankedLeagueQueue")) {
                rankedLeagueQueue = arguments.get("rankedLeagueQueue").getAsString();
            }

            if (arguments.has("challengeCrystalLevel")) {
                challengeCrystalLevel = arguments.get("challengeCrystalLevel").getAsString();
            }

            if (arguments.has("masteryScore")) {
                masteryScore = arguments.get("masteryScore").getAsInt();
            }

            if (arguments.has("availability")) {
                availability = arguments.get("availability").getAsString();
            }

            if (arguments.has("rankedLeagueTier")) {
                rankedLeagueTier = arguments.get("rankedLeagueTier").getAsString();
            }

            JsonObject body = buildChatAppearanceOverride();
            sendChatAppearanceOverride(body);
            return true;
        } catch (Exception e) {
            mainInitiator.log("Failed to set task arguments");
        }
        return false;
    }

    public JsonObject getTaskArgs() {
        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("iconId", iconId);
        taskArgs.addProperty("challengePoints", challengePoints);
        taskArgs.addProperty("rankedLeagueQueue", rankedLeagueQueue);
        taskArgs.addProperty("challengeCrystalLevel", challengeCrystalLevel);
        taskArgs.addProperty("masteryScore", masteryScore);
        taskArgs.addProperty("availability", availability);
        taskArgs.addProperty("rankedLeagueTier", rankedLeagueTier);

        return taskArgs;
    }

    private JsonArray getRankedLeagueQueueOptions() {
        JsonObject rankedSoloDuo = new JsonObject();
        rankedSoloDuo.addProperty("name", "Ranked Solo/Duo");
        rankedSoloDuo.addProperty("value", "RANKED_SOLO_5x5");

        JsonObject rankedFlex = new JsonObject();
        rankedFlex.addProperty("name", "Ranked Flex");
        rankedFlex.addProperty("value", "RANKED_FLEX_SR");

        JsonObject rankedTFT = new JsonObject();
        rankedTFT.addProperty("name", "Ranked TFT");
        rankedTFT.addProperty("value", "RANKED_TFT");

        JsonArray rankedLeagueQueueOptions = new JsonArray();
        rankedLeagueQueueOptions.add(rankedSoloDuo);
        rankedLeagueQueueOptions.add(rankedFlex);
        rankedLeagueQueueOptions.add(rankedTFT);
        return rankedLeagueQueueOptions;
    }

    private JsonArray getRankedLeagueTierOptions() {
        JsonObject rankedIron = new JsonObject();
        rankedIron.addProperty("name", "Iron");
        rankedIron.addProperty("value", "IRON");

        JsonObject rankedBronze = new JsonObject();
        rankedBronze.addProperty("name", "Bronze");
        rankedBronze.addProperty("value", "BRONZE");

        JsonObject rankedSilver = new JsonObject();
        rankedSilver.addProperty("name", "Silver");
        rankedSilver.addProperty("value", "SILVER");

        JsonObject rankedGold = new JsonObject();
        rankedGold.addProperty("name", "Gold");
        rankedGold.addProperty("value", "GOLD");

        JsonObject rankedPlatinum = new JsonObject();
        rankedPlatinum.addProperty("name", "Platinum");
        rankedPlatinum.addProperty("value", "PLATINUM");

        JsonObject rankedEmerald = new JsonObject();
        rankedEmerald.addProperty("name", "Emerald");
        rankedEmerald.addProperty("value", "EMERALD");

        JsonObject rankedDiamond = new JsonObject();
        rankedDiamond.addProperty("name", "Diamond");
        rankedDiamond.addProperty("value", "DIAMOND");

        JsonObject rankedMaster = new JsonObject();
        rankedMaster.addProperty("name", "Master");
        rankedMaster.addProperty("value", "MASTER");

        JsonObject rankedGrandmaster = new JsonObject();
        rankedGrandmaster.addProperty("name", "Grandmaster");
        rankedGrandmaster.addProperty("value", "GRANDMASTER");

        JsonObject rankedChallenger = new JsonObject();
        rankedChallenger.addProperty("name", "Challenger");
        rankedChallenger.addProperty("value", "CHALLENGER");

        JsonArray rankedLeagueQueueOptions = new JsonArray();
        rankedLeagueQueueOptions.add(rankedIron);
        rankedLeagueQueueOptions.add(rankedBronze);
        rankedLeagueQueueOptions.add(rankedSilver);
        rankedLeagueQueueOptions.add(rankedGold);
        rankedLeagueQueueOptions.add(rankedPlatinum);
        rankedLeagueQueueOptions.add(rankedEmerald);
        rankedLeagueQueueOptions.add(rankedDiamond);
        rankedLeagueQueueOptions.add(rankedMaster);
        rankedLeagueQueueOptions.add(rankedGrandmaster);
        rankedLeagueQueueOptions.add(rankedChallenger);
        return rankedLeagueQueueOptions;
    }

    private JsonArray getAvailabilityOptions() {
        JsonObject available = new JsonObject();
        available.addProperty("name", "Available");
        available.addProperty("value", "chat");

        JsonObject busy = new JsonObject();
        busy.addProperty("name", "Busy");
        busy.addProperty("value", "dnd");

        JsonObject away = new JsonObject();
        away.addProperty("name", "Away");
        away.addProperty("value", "away");

        JsonObject offline = new JsonObject();
        offline.addProperty("name", "Offline");
        offline.addProperty("value", "offline");

        JsonObject mobile = new JsonObject();
        mobile.addProperty("name", "Mobile");
        mobile.addProperty("value", "mobile");

        JsonArray availabilityOptions = new JsonArray();
        availabilityOptions.add(available);
        availabilityOptions.add(busy);
        availabilityOptions.add(away);
        availabilityOptions.add(offline);
        availabilityOptions.add(mobile);
        return availabilityOptions;
    }

    private JsonArray getChallengeCrystalLevelOptions() {
        JsonObject rankedIron = new JsonObject();
        rankedIron.addProperty("name", "Iron");
        rankedIron.addProperty("value", "IRON");

        JsonObject rankedBronze = new JsonObject();
        rankedBronze.addProperty("name", "Bronze");
        rankedBronze.addProperty("value", "BRONZE");

        JsonObject rankedSilver = new JsonObject();
        rankedSilver.addProperty("name", "Silver");
        rankedSilver.addProperty("value", "SILVER");

        JsonObject rankedGold = new JsonObject();
        rankedGold.addProperty("name", "Gold");
        rankedGold.addProperty("value", "GOLD");

        JsonObject rankedPlatinum = new JsonObject();
        rankedPlatinum.addProperty("name", "Platinum");
        rankedPlatinum.addProperty("value", "PLATINUM");

        JsonObject rankedDiamond = new JsonObject();
        rankedDiamond.addProperty("name", "Diamond");
        rankedDiamond.addProperty("value", "DIAMOND");

        JsonObject rankedMaster = new JsonObject();
        rankedMaster.addProperty("name", "Master");
        rankedMaster.addProperty("value", "MASTER");

        JsonObject rankedGrandmaster = new JsonObject();
        rankedGrandmaster.addProperty("name", "Grandmaster");
        rankedGrandmaster.addProperty("value", "GRANDMASTER");

        JsonObject rankedChallenger = new JsonObject();
        rankedChallenger.addProperty("name", "Challenger");
        rankedChallenger.addProperty("value", "CHALLENGER");

        JsonArray challengeCrystalLevelOptions = new JsonArray();
        challengeCrystalLevelOptions.add(rankedIron);
        challengeCrystalLevelOptions.add(rankedBronze);
        challengeCrystalLevelOptions.add(rankedSilver);
        challengeCrystalLevelOptions.add(rankedGold);
        challengeCrystalLevelOptions.add(rankedPlatinum);
        challengeCrystalLevelOptions.add(rankedDiamond);
        challengeCrystalLevelOptions.add(rankedMaster);
        challengeCrystalLevelOptions.add(rankedGrandmaster);
        challengeCrystalLevelOptions.add(rankedChallenger);
        return challengeCrystalLevelOptions;
    }

    private JsonArray buildRequiredArgs() {
        JsonArray requiredArgs = new JsonArray();
        JsonObject iconId = new JsonObject();
        iconId.addProperty("displayName", "Icon ID");
        iconId.addProperty("backendKey", "iconId");
        iconId.addProperty("type", INPUT_TYPE.NUMBER.toString());
        iconId.addProperty("required", false);
        iconId.addProperty("currentValue", this.iconId);
        iconId.addProperty("description", "The icon ID to display for other players");
        requiredArgs.add(iconId);

        JsonObject challengePoints = new JsonObject();
        challengePoints.addProperty("displayName", "Challenge Points");
        challengePoints.addProperty("backendKey", "challengePoints");
        challengePoints.addProperty("type", INPUT_TYPE.NUMBER.toString());
        challengePoints.addProperty("required", false);
        challengePoints.addProperty("currentValue", this.challengePoints);
        challengePoints.addProperty("description", "The challenge points to display in your Hovercard");
        requiredArgs.add(challengePoints);

        JsonObject rankedLeagueQueue = new JsonObject();
        rankedLeagueQueue.addProperty("displayName", "Ranked League Queue");
        rankedLeagueQueue.addProperty("backendKey", "rankedLeagueQueue");
        rankedLeagueQueue.addProperty("type", INPUT_TYPE.SELECT.toString());
        rankedLeagueQueue.add("options", getRankedLeagueQueueOptions());
        rankedLeagueQueue.addProperty("required", false);
        rankedLeagueQueue.addProperty("currentValue", this.rankedLeagueQueue);
        rankedLeagueQueue.addProperty("description", "The rank queue Type to display in your Hovercard");
        requiredArgs.add(rankedLeagueQueue);

        JsonObject rankedLeagueTier = new JsonObject();
        rankedLeagueTier.addProperty("displayName", "Ranked League Tier");
        rankedLeagueTier.addProperty("backendKey", "rankedLeagueTier");
        rankedLeagueTier.addProperty("type", INPUT_TYPE.SELECT.toString());
        rankedLeagueTier.add("options", getRankedLeagueTierOptions());
        rankedLeagueTier.addProperty("required", false);
        rankedLeagueTier.addProperty("currentValue", this.rankedLeagueTier);
        rankedLeagueTier.addProperty("description", "The ranked league tier to display in your Hovercard");
        requiredArgs.add(rankedLeagueTier);

        JsonObject challengeCrystalLevel = new JsonObject();
        challengeCrystalLevel.addProperty("displayName", "Challenge Crystal Level");
        challengeCrystalLevel.addProperty("backendKey", "challengeCrystalLevel");
        challengeCrystalLevel.addProperty("type", INPUT_TYPE.SELECT.toString());
        challengeCrystalLevel.add("options", getChallengeCrystalLevelOptions());
        challengeCrystalLevel.addProperty("required", false);
        challengeCrystalLevel.addProperty("currentValue", this.challengeCrystalLevel);
        challengeCrystalLevel.addProperty("description", "The challenge crystal level to display in your Hovercard");
        requiredArgs.add(challengeCrystalLevel);

        JsonObject masteryScore = new JsonObject();
        masteryScore.addProperty("displayName", "Mastery Score");
        masteryScore.addProperty("backendKey", "masteryScore");
        masteryScore.addProperty("type", INPUT_TYPE.NUMBER.toString());
        masteryScore.addProperty("required", false);
        masteryScore.addProperty("currentValue", this.masteryScore);
        masteryScore.addProperty("description", "The mastery score to display in your Hovercard");
        requiredArgs.add(masteryScore);

        JsonObject availability = new JsonObject();
        availability.addProperty("displayName", "Availability");
        availability.addProperty("backendKey", "availability");
        availability.addProperty("type", INPUT_TYPE.SELECT.toString());
        availability.add("options", getAvailabilityOptions());
        availability.addProperty("required", false);
        availability.addProperty("currentValue", this.availability);
        availability.addProperty("description", "The availability to display in your Hovercard");
        requiredArgs.add(availability);

        return requiredArgs;
    }

    public JsonArray getRequiredArgs() {
        //This approach breaks the current value of the requiredArgsbuildRequiredArgs();
        return buildRequiredArgs();
    }
}
