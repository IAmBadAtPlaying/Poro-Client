package com.iambadatplaying.tasks;

import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

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

    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        JSONObject data = webSocketEvent.getJSONObject(2);
        String uriTrigger = data.getString("uri");
        if (lol_gameflow_v1_gameflow_phase.equals(uriTrigger)) {
            handleUpdateData(data);
        }
    }

    private JSONObject buildChatAppearanceOverride() {
        JSONObject chatAppearanceOverride = new JSONObject();
        try {
            JSONObject lol = new JSONObject();
            if (challengePoints != null) {
                lol.put("challengePoints", challengePoints.toString());
            }
            if (masteryScore != null) {
                lol.put("masteryScore", masteryScore.toString());
            }
            lol.put("rankedLeagueQueue", rankedLeagueQueue);
            lol.put("challengeCrystalLevel", challengeCrystalLevel);
            lol.put("rankedLeagueTier", rankedLeagueTier);

            chatAppearanceOverride.put("lol", lol);

            if (iconId != null) {
                chatAppearanceOverride.put("icon", iconId.intValue());
            }

            chatAppearanceOverride.put("availability", availability);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mainInitiator.log(chatAppearanceOverride.toString());
        return chatAppearanceOverride;
    }

    private void handleUpdateData(JSONObject updateData) {
        if (updateData == null || updateData.isEmpty()) return;
        String newGameflowPhase = updateData.getString("data");
        if ("EndOfGame".equals(newGameflowPhase)) {
            JSONObject body = buildChatAppearanceOverride();
            sendChatAppearanceOverride(body);
        }
    }

    private void sendChatAppearanceOverride(JSONObject body) {
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

    public boolean setTaskArgs(JSONObject arguments) {
        try {
            if (arguments.has("iconId")) {
                iconId = arguments.getInt("iconId");
            }

            if (arguments.has("challengePoints")) {
                challengePoints = arguments.getInt("challengePoints");
            }

            if (arguments.has("rankedLeagueQueue")) {
                rankedLeagueQueue = arguments.getString("rankedLeagueQueue");
            }

            if (arguments.has("challengeCrystalLevel")) {
                challengeCrystalLevel = arguments.getString("challengeCrystalLevel");
            }

            if (arguments.has("masteryScore")) {
                masteryScore = arguments.getInt("masteryScore");
            }

            if (arguments.has("availability")) {
                availability = arguments.getString("availability");
            }

            if (arguments.has("rankedLeagueTier")) {
                rankedLeagueTier = arguments.getString("rankedLeagueTier");
            }

            JSONObject body = buildChatAppearanceOverride();
            sendChatAppearanceOverride(body);
            return true;
        } catch (Exception e) {
            mainInitiator.log("Failed to set task arguments");
        }
        return false;
    }

    public JSONObject getTaskArgs() {
        JSONObject taskArgs = new JSONObject();
        taskArgs.put("iconId", iconId);
        taskArgs.put("challengePoints", challengePoints);
        taskArgs.put("rankedLeagueQueue", rankedLeagueQueue);
        taskArgs.put("challengeCrystalLevel", challengeCrystalLevel);
        taskArgs.put("masteryScore", masteryScore);
        taskArgs.put("availability", availability);
        taskArgs.put("rankedLeagueTier", rankedLeagueTier);

        return taskArgs;
    }

    private JSONArray getRankedLeagueQueueOptions() {
        JSONObject rankedSoloDuo = new JSONObject();
        rankedSoloDuo.put("name", "Ranked Solo/Duo");
        rankedSoloDuo.put("value", "RANKED_SOLO_5x5");

        JSONObject rankedFlex = new JSONObject();
        rankedFlex.put("name", "Ranked Flex");
        rankedFlex.put("value", "RANKED_FLEX_SR");

        JSONObject rankedTFT = new JSONObject();
        rankedTFT.put("name", "Ranked TFT");
        rankedTFT.put("value", "RANKED_TFT");

        JSONArray rankedLeagueQueueOptions = new JSONArray();
        rankedLeagueQueueOptions.put(rankedSoloDuo);
        rankedLeagueQueueOptions.put(rankedFlex);
        rankedLeagueQueueOptions.put(rankedTFT);
        return rankedLeagueQueueOptions;
    }

    private JSONArray getRankedLeagueTierOptions() {
        JSONObject rankedIron = new JSONObject();
        rankedIron.put("name", "Iron");
        rankedIron.put("value", "IRON");

        JSONObject rankedBronze = new JSONObject();
        rankedBronze.put("name", "Bronze");
        rankedBronze.put("value", "BRONZE");

        JSONObject rankedSilver = new JSONObject();
        rankedSilver.put("name", "Silver");
        rankedSilver.put("value", "SILVER");

        JSONObject rankedGold = new JSONObject();
        rankedGold.put("name", "Gold");
        rankedGold.put("value", "GOLD");

        JSONObject rankedPlatinum = new JSONObject();
        rankedPlatinum.put("name", "Platinum");
        rankedPlatinum.put("value", "PLATINUM");

        JSONObject rankedEmerald = new JSONObject();
        rankedEmerald.put("name", "Emerald");
        rankedEmerald.put("value", "EMERALD");

        JSONObject rankedDiamond = new JSONObject();
        rankedDiamond.put("name", "Diamond");
        rankedDiamond.put("value", "DIAMOND");

        JSONObject rankedMaster = new JSONObject();
        rankedMaster.put("name", "Master");
        rankedMaster.put("value", "MASTER");

        JSONObject rankedGrandmaster = new JSONObject();
        rankedGrandmaster.put("name", "Grandmaster");
        rankedGrandmaster.put("value", "GRANDMASTER");

        JSONObject rankedChallenger = new JSONObject();
        rankedChallenger.put("name", "Challenger");
        rankedChallenger.put("value", "CHALLENGER");

        JSONArray rankedLeagueQueueOptions = new JSONArray();
        rankedLeagueQueueOptions.put(rankedIron);
        rankedLeagueQueueOptions.put(rankedBronze);
        rankedLeagueQueueOptions.put(rankedSilver);
        rankedLeagueQueueOptions.put(rankedGold);
        rankedLeagueQueueOptions.put(rankedPlatinum);
        rankedLeagueQueueOptions.put(rankedEmerald);
        rankedLeagueQueueOptions.put(rankedDiamond);
        rankedLeagueQueueOptions.put(rankedMaster);
        rankedLeagueQueueOptions.put(rankedGrandmaster);
        rankedLeagueQueueOptions.put(rankedChallenger);
        return rankedLeagueQueueOptions;
    }

    private JSONArray getAvailabilityOptions() {
        JSONObject available = new JSONObject();
        available.put("name", "Available");
        available.put("value", "chat");

        JSONObject busy = new JSONObject();
        busy.put("name", "Busy");
        busy.put("value", "dnd");

        JSONObject away = new JSONObject();
        away.put("name", "Away");
        away.put("value", "away");

        JSONObject offline = new JSONObject();
        offline.put("name", "Offline");
        offline.put("value", "offline");

        JSONObject mobile = new JSONObject();
        mobile.put("name", "Mobile");
        mobile.put("value", "mobile");

        JSONArray availabilityOptions = new JSONArray();
        availabilityOptions.put(available);
        availabilityOptions.put(busy);
        availabilityOptions.put(away);
        availabilityOptions.put(offline);
        availabilityOptions.put(mobile);
        return availabilityOptions;
    }

    private JSONArray getChallengeCrystalLevelOptions() {
        JSONObject rankedIron = new JSONObject();
        rankedIron.put("name", "Iron");
        rankedIron.put("value", "IRON");

        JSONObject rankedBronze = new JSONObject();
        rankedBronze.put("name", "Bronze");
        rankedBronze.put("value", "BRONZE");

        JSONObject rankedSilver = new JSONObject();
        rankedSilver.put("name", "Silver");
        rankedSilver.put("value", "SILVER");

        JSONObject rankedGold = new JSONObject();
        rankedGold.put("name", "Gold");
        rankedGold.put("value", "GOLD");

        JSONObject rankedPlatinum = new JSONObject();
        rankedPlatinum.put("name", "Platinum");
        rankedPlatinum.put("value", "PLATINUM");

        JSONObject rankedDiamond = new JSONObject();
        rankedDiamond.put("name", "Diamond");
        rankedDiamond.put("value", "DIAMOND");

        JSONObject rankedMaster = new JSONObject();
        rankedMaster.put("name", "Master");
        rankedMaster.put("value", "MASTER");

        JSONObject rankedGrandmaster = new JSONObject();
        rankedGrandmaster.put("name", "Grandmaster");
        rankedGrandmaster.put("value", "GRANDMASTER");

        JSONObject rankedChallenger = new JSONObject();
        rankedChallenger.put("name", "Challenger");
        rankedChallenger.put("value", "CHALLENGER");

        JSONArray challengeCrystalLevelOptions = new JSONArray();
        challengeCrystalLevelOptions.put(rankedIron);
        challengeCrystalLevelOptions.put(rankedBronze);
        challengeCrystalLevelOptions.put(rankedSilver);
        challengeCrystalLevelOptions.put(rankedGold);
        challengeCrystalLevelOptions.put(rankedPlatinum);
        challengeCrystalLevelOptions.put(rankedDiamond);
        challengeCrystalLevelOptions.put(rankedMaster);
        challengeCrystalLevelOptions.put(rankedGrandmaster);
        challengeCrystalLevelOptions.put(rankedChallenger);
        return challengeCrystalLevelOptions;
    }

    private JSONArray buildRequiredArgs() {
        JSONArray requiredArgs = new JSONArray();
        JSONObject iconId = new JSONObject();
        iconId.put("displayName", "Icon ID");
        iconId.put("backendKey", "iconId");
        iconId.put("type", INPUT_TYPE.NUMBER.toString());
        iconId.put("required", false);
        iconId.put("currentValue", this.iconId);
        iconId.put("description", "The icon ID to display for other players");
        requiredArgs.put(iconId);

        JSONObject challengePoints = new JSONObject();
        challengePoints.put("displayName", "Challenge Points");
        challengePoints.put("backendKey", "challengePoints");
        challengePoints.put("type", INPUT_TYPE.NUMBER.toString());
        challengePoints.put("required", false);
        challengePoints.put("currentValue", this.challengePoints);
        challengePoints.put("description", "The challenge points to display in your Hovercard");
        requiredArgs.put(challengePoints);

        JSONObject rankedLeagueQueue = new JSONObject();
        rankedLeagueQueue.put("displayName", "Ranked League Queue");
        rankedLeagueQueue.put("backendKey", "rankedLeagueQueue");
        rankedLeagueQueue.put("type", INPUT_TYPE.SELECT.toString());
        rankedLeagueQueue.put("options", getRankedLeagueQueueOptions());
        rankedLeagueQueue.put("required", false);
        rankedLeagueQueue.put("currentValue", this.rankedLeagueQueue);
        rankedLeagueQueue.put("description", "The rank queue Type to display in your Hovercard");
        requiredArgs.put(rankedLeagueQueue);

        JSONObject rankedLeagueTier = new JSONObject();
        rankedLeagueTier.put("displayName", "Ranked League Tier");
        rankedLeagueTier.put("backendKey", "rankedLeagueTier");
        rankedLeagueTier.put("type", INPUT_TYPE.SELECT.toString());
        rankedLeagueTier.put("options", getRankedLeagueTierOptions());
        rankedLeagueTier.put("required", false);
        rankedLeagueTier.put("currentValue", this.rankedLeagueTier);
        rankedLeagueTier.put("description", "The ranked league tier to display in your Hovercard");
        requiredArgs.put(rankedLeagueTier);

        JSONObject challengeCrystalLevel = new JSONObject();
        challengeCrystalLevel.put("displayName", "Challenge Crystal Level");
        challengeCrystalLevel.put("backendKey", "challengeCrystalLevel");
        challengeCrystalLevel.put("type", INPUT_TYPE.SELECT.toString());
        challengeCrystalLevel.put("options", getChallengeCrystalLevelOptions());
        challengeCrystalLevel.put("required", false);
        challengeCrystalLevel.put("currentValue", this.challengeCrystalLevel);
        challengeCrystalLevel.put("description", "The challenge crystal level to display in your Hovercard");
        requiredArgs.put(challengeCrystalLevel);

        JSONObject masteryScore = new JSONObject();
        masteryScore.put("displayName", "Mastery Score");
        masteryScore.put("backendKey", "masteryScore");
        masteryScore.put("type", INPUT_TYPE.NUMBER.toString());
        masteryScore.put("required", false);
        masteryScore.put("currentValue", this.masteryScore);
        masteryScore.put("description", "The mastery score to display in your Hovercard");
        requiredArgs.put(masteryScore);

        JSONObject availability = new JSONObject();
        availability.put("displayName", "Availability");
        availability.put("backendKey", "availability");
        availability.put("type", INPUT_TYPE.SELECT.toString());
        availability.put("options", getAvailabilityOptions());
        availability.put("required", false);
        availability.put("currentValue", this.availability);
        availability.put("description", "The availability to display in your Hovercard");
        requiredArgs.put(availability);

        return requiredArgs;
    }

    public JSONArray getRequiredArgs() {
        //This approach breaks the current value of the requiredArgsbuildRequiredArgs();
        return buildRequiredArgs();
    }
}
