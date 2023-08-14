package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class ChatAppearanceOverride implements Task{

    private volatile boolean running = false;

    private final String lol_gameflow_v1_gameflow_phase = "OnJsonApiEvent_lol-gameflow_v1_gameflow-phase";
    private final String[] apiTriggerEvents = {lol_gameflow_v1_gameflow_phase};

    private Integer iconId;
    private Integer challengePoints;
    private String rankedLeagueQueue;
    private String rankedLeagueTier;
    private String challengeCrystalLevel;
    private Integer masteryScore;

    private String availability;

    private MainInitiator mainInitiator;

    @Override
    public void notify(JSONArray webSocketEvent) {
        if (!running || mainInitiator == null || webSocketEvent.isEmpty() || webSocketEvent.length() < 3) {
            return;
        }
        String apiTrigger = webSocketEvent.getString(1);
        switch (apiTrigger) {
            case lol_gameflow_v1_gameflow_phase:
                JSONObject updateObject = webSocketEvent.getJSONObject(2);
                handleUpdateData(updateObject);
            break;
            default:
            break;
        }
    }

    private JSONObject buildChatAppearanceOverride() {
        JSONObject chatAppearanceOverride = new JSONObject();
        try {
            JSONObject lol = new JSONObject();
            lol.put("challengePoints", challengePoints.toString());
            lol.put("rankedLeagueQueue", rankedLeagueQueue);
            lol.put("challengeCrystalLevel", challengeCrystalLevel);
            lol.put("masteryScore", masteryScore.toString());
            lol.put("rankedLeagueTier", rankedLeagueTier);

            chatAppearanceOverride.put("lol", lol);
            chatAppearanceOverride.put("icon", iconId.intValue());
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
        if("EndOfGame".equals(newGameflowPhase)) {
            JSONObject body = buildChatAppearanceOverride();
            sendChatAppearanceOverride(body);
        }
    }

    private void sendChatAppearanceOverride(JSONObject body) {
        try {
            HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT,"/lol-chat/v1/me", body.toString());
            String response = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, con);
            mainInitiator.log(response);
            con.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String[] getTriggerApiEvents() {
        return apiTriggerEvents;
    }

    @Override
    public void setMainInitiator(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    @Override
    public void init() {
        this.running = true;
    }

    @Override
    public void shutdown() {
        this.running = false;
    }

    @Override
    public boolean setTaskArgs(JSONObject arguments) {
        try {
            iconId = arguments.getInt("iconId");
            challengePoints = arguments.getInt("challengePoints");
            rankedLeagueQueue = arguments.getString("rankedLeagueQueue");
            challengeCrystalLevel = arguments.getString("challengeCrystalLevel");
            masteryScore = arguments.getInt("masteryScore");
            availability = arguments.getString("availability");
            rankedLeagueTier = arguments.getString("rankedLeagueTier");

            JSONObject body = buildChatAppearanceOverride();
            sendChatAppearanceOverride(body);
            return true;
        } catch (Exception e) {
            mainInitiator.log("Failed to set task arguments");
        }
        return false;
    }

    @Override
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

    @Override
    public JSONArray getRequiredArgs() {
        JSONArray requiredArgs = new JSONArray();
        JSONObject iconId = new JSONObject();
        iconId.put("displayName", "Icon ID");
        iconId.put("backendKey", "iconId");
        iconId.put("type", "Integer");
        iconId.put("required", false);
        iconId.put("currentValue", this.iconId);
        iconId.put("description", "The icon ID to display for other players");
        requiredArgs.put(iconId);

        JSONObject challengePoints = new JSONObject();
        challengePoints.put("displayName", "Challenge Points");
        challengePoints.put("backendKey", "challengePoints");
        challengePoints.put("type", "Integer");
        challengePoints.put("required", false);
        challengePoints.put("currentValue", this.challengePoints);
        challengePoints.put("description", "The challenge points to display in your Hovercard");
        requiredArgs.put(challengePoints);

        JSONObject rankedLeagueQueue = new JSONObject();
        rankedLeagueQueue.put("displayName", "Ranked League Queue");
        rankedLeagueQueue.put("backendKey", "rankedLeagueQueue");
        rankedLeagueQueue.put("type", "String");
        rankedLeagueQueue.put("required", false);
        rankedLeagueQueue.put("currentValue", this.rankedLeagueQueue);
        rankedLeagueQueue.put("description", "The rank queue Type to display in your Hovercard");
        requiredArgs.put(rankedLeagueQueue);

        JSONObject rankedLeagueTier = new JSONObject();
        rankedLeagueTier.put("displayName", "Ranked League Tier");
        rankedLeagueTier.put("backendKey", "rankedLeagueTier");
        rankedLeagueTier.put("type", "String");
        rankedLeagueTier.put("required", false);
        rankedLeagueTier.put("currentValue", this.rankedLeagueTier);
        rankedLeagueTier.put("description", "The ranked league tier to display in your Hovercard");
        requiredArgs.put(rankedLeagueTier);

        JSONObject challengeCrystalLevel = new JSONObject();
        challengeCrystalLevel.put("displayName", "Challenge Crystal Level");
        challengeCrystalLevel.put("backendKey", "challengeCrystalLevel");
        challengeCrystalLevel.put("type", "String");
        challengeCrystalLevel.put("required", false);
        challengeCrystalLevel.put("currentValue", this.challengeCrystalLevel);
        challengeCrystalLevel.put("description", "The challenge crystal level to display in your Hovercard");
        requiredArgs.put(challengeCrystalLevel);

        JSONObject masteryScore = new JSONObject();
        masteryScore.put("displayName", "Mastery Score");
        masteryScore.put("backendKey", "masteryScore");
        masteryScore.put("type", "Integer");
        masteryScore.put("required", false);
        masteryScore.put("currentValue", this.masteryScore);
        masteryScore.put("description", "The mastery score to display in your Hovercard");
        requiredArgs.put(masteryScore);

        JSONObject availability = new JSONObject();
        availability.put("displayName", "Availability");
        availability.put("backendKey", "availability");
        availability.put("type", "String");
        availability.put("required", false);
        availability.put("currentValue", this.availability);
        availability.put("description", "The availability to display in your Hovercard");
        requiredArgs.put(availability);

        return requiredArgs;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
