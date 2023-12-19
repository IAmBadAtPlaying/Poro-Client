package com.iambadatplaying.data.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ReworkedChampSelectData extends StateDataManager {
    public static final String INSTRUCTION_PLAY_SOUND = ReworkedDataManager.INSTRUCTION_PREFIX + "PlaySound";

    private static final String JSON_KEY_PHASE = "phase";
    private static final String JSON_KEY_BAN_ACTION = "banAction";
    private static final String JSON_KEY_PICK_ACTION = "pickAction";
    private static final String JSON_KEY_IS_IN_PROGRESS = "isInProgress";
    private static final String JSON_KEY_COMPLETED = "completed";
    private static final String JSON_KEY_LOCAL_PLAYER_CELL_ID = "localPlayerCellId";
    private static final String JSON_KEY_STATE = "state";

    private static final String UPDATE_TYPE_CHAMP_SELECT = "ChampSelectUpdate";

    private static final String CHAMP_SELECT_SESSION_URI = "/lol-champ-select/v1/session";

    public ReworkedChampSelectData(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    private enum ChampSelectState {
        PREPARATION,
        BANNING,
        AWAITING_BAN_RESULTS,
        AWAITING_PICK,
        PICKING_WITHOUT_BAN,
        PICKING_WITH_BAN,
        AWAITING_FINALIZATION,
        FINALIZATION;

        // Logic breaks in tournament draft (kinda)
        // Suppressing warnings as there is no other way to do this
        @java.lang.SuppressWarnings({"squid:S6541", "squid:S3776"})
        public static ChampSelectState fromParameters(JsonObject parameters) {
            if (parameters == null) return null;
            String timerPhase = parameters.get(JSON_KEY_PHASE).getAsString();
            if (timerPhase == null) {
                timerPhase = "UNKNOWN";
            }
            boolean banExists = parameters.has(JSON_KEY_BAN_ACTION) && !parameters.get(JSON_KEY_BAN_ACTION).getAsJsonObject().isEmpty();
            boolean pickExists = parameters.has(JSON_KEY_PICK_ACTION) && !parameters.get(JSON_KEY_PICK_ACTION).getAsJsonObject().isEmpty();

            boolean isPickInProgress = false;
            boolean isPickCompleted = false;

            boolean isBanInProgress = false;
            boolean isBanCompleted = false;

            if (pickExists) {
                JsonObject pickAction = parameters.get(JSON_KEY_PICK_ACTION).getAsJsonObject();
                isPickInProgress = pickAction.get(JSON_KEY_IS_IN_PROGRESS).getAsBoolean();
                isPickCompleted = pickAction.get(JSON_KEY_COMPLETED).getAsBoolean();
            }
            if (banExists) {
                JsonObject banAction = parameters.get(JSON_KEY_BAN_ACTION).getAsJsonObject();
                isBanInProgress = banAction.get(JSON_KEY_IS_IN_PROGRESS).getAsBoolean();
                isBanCompleted = banAction.get(JSON_KEY_COMPLETED).getAsBoolean();
            }

            switch (timerPhase) {
                case "PLANNING":
                    return PREPARATION;
                case "BAN_PICK":
                    if (banExists) {
                        if (isBanInProgress) {
                            return BANNING;
                        } else {
                            if (isBanCompleted) {
                                if (pickExists) {
                                    if (isPickInProgress) {
                                        return PICKING_WITH_BAN;
                                    } else {
                                        if (isPickCompleted) {
                                            return AWAITING_FINALIZATION;
                                        } else {
                                            return AWAITING_PICK;
                                        }
                                    }
                                } else {
                                    return AWAITING_FINALIZATION;
                                }
                            } else {
                                return AWAITING_BAN_RESULTS;
                            }
                        }
                    } else {
                        if (pickExists) {
                            if (isPickInProgress) {
                                return PICKING_WITHOUT_BAN;
                            } else {
                                if (isPickCompleted) {
                                    return AWAITING_FINALIZATION;
                                } else {
                                    return AWAITING_PICK;
                                }
                            }
                        } else {
                            return AWAITING_FINALIZATION;
                        }
                    }
                case "FINALIZATION":
                    return FINALIZATION;
                default:
                    return null;
            }
        }
    }

    private Map<Integer, JsonObject> cellIdMemberMap;
    private Map<Integer, JsonObject> banMap;
    private Map<Integer, JsonObject> pickMap;

    @Override
    protected void doInitialize() {
        cellIdMemberMap = Collections.synchronizedMap(new HashMap<>());
        banMap = Collections.synchronizedMap(new HashMap<>());
        pickMap = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return CHAMP_SELECT_SESSION_URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case "Create":
            case "Update":
                if (!data.isJsonObject()) return;
                Optional<JsonObject> updatedFEData = backendToFrontendChampSelectSession(data.getAsJsonObject());
                if (!updatedFEData.isPresent()) return;
                JsonObject updatedState = updatedFEData.get();
                if (Util.equalJsonElements(updatedState, currentState)) return;
                currentState = updatedState;
                sendCurrentState();
                break;
            case "Delete":
                resetSession();
                break;
            default:
                log("Unknown event type: " + type, MainInitiator.LOG_LEVEL.ERROR);
                break;
        }
    }

    private Optional<JsonObject> backendToFrontendChampSelectSession(JsonObject data) {
        boolean isCustomGame = Util.getBoolean(data, "isCustomGame", false);
        JsonObject frontendChampSelect = new JsonObject();

        Util.copyJsonAttributes(data, frontendChampSelect, JSON_KEY_LOCAL_PLAYER_CELL_ID, "gameId", "hasSimultaneousBans", "skipChampionSelect", "benchEnabled", "rerollsRemaining");

        Optional<JsonArray> optActions = Util.getOptJSONArray(data, "actions");
        if (!optActions.isPresent()) {
            log("No actions found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        JsonArray actions = optActions.get();
        updateActionMappings(actions);

        if (isCustomGame) {
            setCustomGameBans(data, frontendChampSelect);
        } else setNormalGameBans(frontendChampSelect);

        Optional<JsonObject> optTimer = Util.getOptJSONObject(data, "timer");
        if (!optTimer.isPresent()) {
            log("No timer found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        Optional<JsonArray> optMyTeam = Util.getOptJSONArray(data, "myTeam");
        if (!optMyTeam.isPresent()) {
            log("No myTeam found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        Optional<JsonArray> optTheirTeam = Util.getOptJSONArray(data, "theirTeam");
        if (!optTheirTeam.isPresent()) {
            log("No theirTeam found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        JsonObject timer = optTimer.get();
        JsonArray myTeam = optMyTeam.get();
        JsonArray theirTeam = optTheirTeam.get();

        JsonArray feMyTeam = new JsonArray();
        JsonArray feTheirTeam = new JsonArray();
        JsonObject feTimer = new JsonObject();

        Util.copyJsonAttributes(timer, feTimer, JSON_KEY_PHASE, "isInfinite");


        for (int i = 0, arrayLength = myTeam.size(); i < arrayLength; i++) {
            JsonObject member = myTeam.get(i).getAsJsonObject();
            feMyTeam.add(teamMemberToSessionMap(member, timer));
        }

        for (int i = 0, arrayLength = theirTeam.size(); i < arrayLength; i++) {
            JsonObject member = theirTeam.get(i).getAsJsonObject();
            feTheirTeam.add(teamMemberToSessionMap(member, timer));
        }

        frontendChampSelect.add("myTeam", feMyTeam);
        frontendChampSelect.add("theirTeam", feTheirTeam);
        frontendChampSelect.add("timer", feTimer);

        Util.copyJsonAttributes(timer, feTimer, JSON_KEY_PHASE, "isInfinite");

        return Optional.ofNullable(frontendChampSelect);
    }

    private JsonObject teamMemberToSessionMap(JsonObject feMember, JsonObject timer) {
        Integer cellId = feMember.get("cellId").getAsInt();
        JsonObject banAction = banMap.get(cellId);
        JsonObject pickAction = pickMap.get(cellId);

        if (banAction == null) {
            banAction = new JsonObject();
        }

        if (pickAction == null) {
            pickAction = new JsonObject();
        }

        feMember.add(JSON_KEY_BAN_ACTION, banAction);
        feMember.add(JSON_KEY_PICK_ACTION, pickAction);

        JsonObject phaseObject = new JsonObject();
        Util.copyJsonAttrib(JSON_KEY_PHASE, timer, phaseObject);
        phaseObject.add(JSON_KEY_BAN_ACTION, banAction);
        phaseObject.add(JSON_KEY_PICK_ACTION, pickAction);

        ChampSelectState state = ChampSelectState.fromParameters(phaseObject);
        if (state == null) {
            log(feMember);
            log(timer);
            log("No fitting state found for cellId: " + cellId);
        } else {

            feMember.addProperty(JSON_KEY_STATE, state.name());
        }
        cellIdMemberMap.put(cellId, feMember);
        return feMember;
    }

    private void setCustomGameBans(JsonObject data, JsonObject frontendChampSelect) {
        //Bans already handled in array for each team
        //Actions are only added (at least ban/pick wise) when they actually happen
        Optional<JsonObject> optBans = Util.getOptJSONObject(data, "bans");
        if (!optBans.isPresent()) {
            log("No bans found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return;
        }

        JsonObject bans = optBans.get();

        frontendChampSelect.addProperty("isCustomGame", true);
        frontendChampSelect.add("bans", bans);
    }

    private void updateActionMappings(JsonArray action) {
        if (action.isEmpty()) return;
        int actionLength = action.size();
        for (int i = 0; i < actionLength; i++) {
            JsonArray subAction = action.get(i).getAsJsonArray();
            if (subAction.isEmpty()) continue;
            int subActionLength = subAction.size();
            for (int j = 0; j < subActionLength; j++) {
                JsonObject actionObject = subAction.get(i).getAsJsonObject();
                updateSingleAction(actionObject);
            }
        }
    }

    private void updateSingleAction(JsonObject action) {
        if (action.isEmpty()) return;

        Optional<Integer> optActorCellId = Util.getOptInt(action, "actorCellId");
        if (!optActorCellId.isPresent()) return;
        int actorCellId = optActorCellId.get();

        Optional<String> optType = Util.getOptString(action, "type");
        if (!optType.isPresent()) return;
        String type = optType.get();

        switch (type) {
            case "ban":
                banMap.put(actorCellId, action);
                break;
            case "pick":
                pickMap.put(actorCellId, action);
                break;
            default:
                //Might be TENS_BAN_REVEAL
                log("Unknown action type: " + type, MainInitiator.LOG_LEVEL.DEBUG);
                break;
        }
    }

    private void setNormalGameBans( JsonObject frontendChampSelect) {
        //Bans are NOT handled in array for each team
        //Actions are already all present
        frontendChampSelect.addProperty("isCustomGame", false);

        JsonArray myTeamBans = new JsonArray();
        JsonArray theirTeamBans = new JsonArray();

        setBansFromBanMap(myTeamBans, theirTeamBans);

        JsonObject bans = new JsonObject();

        bans.add("myTeamBans", myTeamBans);
        bans.add("theirTeamBans", theirTeamBans);

        frontendChampSelect.add("bans", bans);;
    }

    private void setBansFromBanMap(JsonArray myTeamBans, JsonArray theirTeamBans) {
        for (JsonObject banAction : banMap.values()) {
            Optional<Boolean> optIsAllyAction = Util.getOptBool(banAction, "isAllyAction");
            if (!optIsAllyAction.isPresent()) continue;

            Optional<Boolean> optIsInProgress = Util.getOptBool(banAction, "isInProgress");
            if (!optIsInProgress.isPresent()) continue;

            Optional<Boolean> optIsCompleted = Util.getOptBool(banAction, "completed");
            if (!optIsCompleted.isPresent()) continue;

            boolean isAllyAction = optIsAllyAction.get();
            boolean isInProgress = optIsInProgress.get();
            boolean isCompleted = optIsCompleted.get();

            if (isInProgress || !isCompleted) {
                if (isAllyAction) {
                    myTeamBans.add(Integer.valueOf(-1));
                } else {
                    theirTeamBans.add(Integer.valueOf(-1));
                }
            }
            if (!isCompleted) continue;

            Optional<Integer> optChampionId = Util.getOptInt(banAction, "championId");
            if (!optChampionId.isPresent()) continue;
            Integer championId = optChampionId.get();

            if (isAllyAction) {
                myTeamBans.add(championId);
            } else {
                theirTeamBans.add(championId);
            }
        }
    }

    private void resetSession() {
        currentState = null;
        cellIdMemberMap.clear();
        banMap.clear();
        pickMap.clear();
    }

    @Override
    protected void doShutdown() {
        currentState = null;
        cellIdMemberMap.clear();
        banMap.clear();
        pickMap.clear();
    }

    @Override
    protected Optional<JsonObject> fetchCurrentState() {
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-champ-select/v1/session");
        JsonObject data = mainInitiator.getConnectionManager().getResponseBodyAsJsonObject(con);
        if (!data.has("errorCode")) return Optional.of(data);
        log("Error while fetching current state: " + data.get("message").getAsString(), MainInitiator.LOG_LEVEL.ERROR);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(com.iambadatplaying.lcuHandler.DataManager.getEventDataString(UPDATE_TYPE_CHAMP_SELECT, currentState));
    }
}
