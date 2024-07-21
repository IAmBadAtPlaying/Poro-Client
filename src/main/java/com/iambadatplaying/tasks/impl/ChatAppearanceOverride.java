package com.iambadatplaying.tasks.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.tasks.ARGUMENT_TYPE;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.builders.TaskArgumentBuilder;
import com.iambadatplaying.tasks.builders.impl.NumberDataBuilder;
import com.iambadatplaying.tasks.builders.impl.SelectDataBuilder;
import com.iambadatplaying.tasks.builders.impl.SelectOption;

import javax.net.ssl.HttpsURLConnection;

public class ChatAppearanceOverride extends Task {

    private final static String lol_gameflow_v1_gameflow_phase = "/lol-gameflow/v1/session";

    private Integer iconId;
    private Integer challengePoints;
    private String  rankedLeagueQueue;
    private String  rankedLeagueTier;
    private String  challengeCrystalLevel;
    private Integer masteryScore;

    private String availability;

    public void notify(JsonArray webSocketEvent) {
        if (!running || starter == null || webSocketEvent.isEmpty() || webSocketEvent.size() < 3) {
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
        starter.log(chatAppearanceOverride.toString());
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
            HttpsURLConnection con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.PUT, "/lol-chat/v1/me", body.toString());
            String response = (String) starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, con);
            starter.log(response);
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
            e.printStackTrace();
            starter.log("Failed to set task arguments");
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

    private JsonObject getRankedLeagueQueueOptions() {
        return new SelectDataBuilder()
                .addOptions(
                        new SelectOption[]{
                                new SelectOption("Ranked Solo/Duo", "RANKED_SOLO_5x5"),
                                new SelectOption("Ranked Flex", "RANKED_FLEX_SR"),
                                new SelectOption("Ranked TFT", "RANKED_TFT")
                        }
                ).build();
    }

    private JsonObject getRankedLeagueTierOptions() {
        return new SelectDataBuilder()
                .addOptions(
                        new SelectOption[]{
                                new SelectOption("Iron", "IRON"),
                                new SelectOption("Bronze", "BRONZE"),
                                new SelectOption("Silver", "SILVER"),
                                new SelectOption("Gold", "GOLD"),
                                new SelectOption("Platinum", "PLATINUM"),
                                new SelectOption("Emerald", "EMERALD"),
                                new SelectOption("Diamond", "DIAMOND"),
                                new SelectOption("Master", "MASTER"),
                                new SelectOption("Grandmaster", "GRANDMASTER"),
                                new SelectOption("Challenger", "CHALLENGER")
                        }
                )
                .build();
    }

    private JsonObject getAvailabilityOptions() {
        return new SelectDataBuilder()
                .addOptions(
                        new SelectOption[] {
                                new SelectOption("Available", "chat"),
                                new SelectOption("Busy", "dnd"),
                                new SelectOption("Away", "away"),
                                new SelectOption("Offline", "offline"),
                                new SelectOption("Mobile", "mobile")
                        }
                )
                .build();
    }

    private JsonObject getChallengeCrystalLevelOptions() {
        return new SelectDataBuilder()
                .addOptions(
                        new SelectOption[]{
                                new SelectOption("Iron", "IRON"),
                                new SelectOption("Bronze", "BRONZE"),
                                new SelectOption("Silver", "SILVER"),
                                new SelectOption("Gold", "GOLD"),
                                new SelectOption("Platinum", "PLATINUM"),
                                new SelectOption("Diamond", "DIAMOND"),
                                new SelectOption("Master", "MASTER"),
                                new SelectOption("Grandmaster", "GRANDMASTER"),
                                new SelectOption("Challenger", "CHALLENGER")
                        }
                )
                .build();
    }

    private JsonArray buildRequiredArgs() {
        JsonArray requiredArgs = new JsonArray();

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Icon ID")
                        .setBackendKey("iconId")
                        .setType(ARGUMENT_TYPE.NUMBER)
                        .setRequired(false)
                        .setCurrentValue(this.iconId)
                        .setDescription("The icon ID to display for other players")
                        .build()
        );


        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Challenge Points")
                        .setBackendKey("challengePoints")
                        .setType(ARGUMENT_TYPE.NUMBER)
                        .setRequired(false)
                        .setCurrentValue(this.challengePoints)
                        .setDescription("The challenge points to display in your Hovercard")
                        .build()
        );


        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Ranked League Queue")
                        .setBackendKey("rankedLeagueQueue")
                        .setType(ARGUMENT_TYPE.SELECT)
                        .setAdditionalData(getRankedLeagueQueueOptions())
                        .setRequired(false)
                        .setCurrentValue(this.rankedLeagueQueue)
                        .setDescription("The rank queue Type to display in your Hovercard")
                        .build()
        );


        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Ranked League Tier")
                        .setBackendKey("rankedLeagueTier")
                        .setType(ARGUMENT_TYPE.SELECT)
                        .setAdditionalData(getRankedLeagueTierOptions())
                        .setRequired(false)
                        .setCurrentValue(this.rankedLeagueTier)
                        .setDescription("The ranked league tier to display in your Hovercard")
                        .build()
        );

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Challenge Crystal Level")
                        .setBackendKey("challengeCrystalLevel")
                        .setType(ARGUMENT_TYPE.SELECT)
                        .setAdditionalData(getChallengeCrystalLevelOptions())
                        .setRequired(false)
                        .setCurrentValue(this.challengeCrystalLevel)
                        .setDescription("The challenge crystal level to display in your Hovercard")
                        .build()
        );

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Mastery Score")
                        .setBackendKey("masteryScore")
                        .setType(ARGUMENT_TYPE.NUMBER)
                        .setRequired(false)
                        .setCurrentValue(this.masteryScore)
                        .setDescription("The mastery score to display in your Hovercard")
                        .build()
        );

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("Availability")
                        .setBackendKey("availability")
                        .setType(ARGUMENT_TYPE.SELECT)
                        .setAdditionalData(getAvailabilityOptions())
                        .setRequired(false)
                        .setCurrentValue(this.availability)
                        .setDescription("The availability to display in your Hovercard")
                        .build()
        );

        return requiredArgs;
    }

    public JsonArray getRequiredArgs() {
        //This approach breaks the current value of the requiredArgsbuildRequiredArgs();
        return buildRequiredArgs();
    }
}
