package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

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
        public static ChampSelectState fromParameters(JSONObject parameters) {
            if (parameters == null) return null;
            String timerPhase = parameters.getString(JSON_KEY_PHASE);
            if (timerPhase == null) {
                timerPhase = "UNKNOWN";
            }
            boolean banExists = parameters.has(JSON_KEY_BAN_ACTION) && !parameters.getJSONObject(JSON_KEY_BAN_ACTION).isEmpty();
            boolean pickExists = parameters.has(JSON_KEY_PICK_ACTION) && !parameters.getJSONObject(JSON_KEY_PICK_ACTION).isEmpty();

            boolean isPickInProgress = false;
            boolean isPickCompleted = false;

            boolean isBanInProgress = false;
            boolean isBanCompleted = false;

            if (pickExists) {
                JSONObject pickAction = parameters.getJSONObject(JSON_KEY_PICK_ACTION);
                isPickInProgress = pickAction.getBoolean(JSON_KEY_IS_IN_PROGRESS);
                isPickCompleted = pickAction.getBoolean(JSON_KEY_COMPLETED);
            }
            if (banExists) {
                JSONObject banAction = parameters.getJSONObject(JSON_KEY_BAN_ACTION);
                isBanInProgress = banAction.getBoolean(JSON_KEY_IS_IN_PROGRESS);
                isBanCompleted = banAction.getBoolean(JSON_KEY_COMPLETED);
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

    private Map<Integer, JSONObject> cellIdMemberMap;
    private Map<Integer, JSONObject> banMap;
    private Map<Integer, JSONObject> pickMap;

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
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        switch (type) {
            case "Create":
            case "Update":
                Optional<JSONObject> updatedFEData = backendToFrontendChampSelectSession(data);
                if (!updatedFEData.isPresent()) return;
                JSONObject updatedState = updatedFEData.get();
                if (updatedState.similar(currentState)) return;
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

    private Optional<JSONObject> backendToFrontendChampSelectSession(JSONObject data) {
        boolean isCustomGame = Util.getBoolean(data, "isCustomGame", false);
        JSONObject frontendChampSelect = new JSONObject();

        Util.copyJsonAttributes(data, frontendChampSelect, JSON_KEY_LOCAL_PLAYER_CELL_ID, "gameId", "hasSimultaneousBans", "skipChampionSelect", "benchEnabled", "rerollsRemaining");

        Optional<JSONArray> optActions = Util.getOptJSONArray(data, "actions");
        if (!optActions.isPresent()) {
            log("No actions found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        JSONArray actions = optActions.get();
        updateActionMappings(actions);

        if (isCustomGame) {
            setCustomGameBans(data, frontendChampSelect);
        } else setNormalGameBans(data, frontendChampSelect);

        Optional<JSONObject> optTimer = Util.getOptJSONObject(data, "timer");
        if (!optTimer.isPresent()) {
            log("No timer found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        Optional<JSONArray> optMyTeam = Util.getOptJSONArray(data, "myTeam");
        if (!optMyTeam.isPresent()) {
            log("No myTeam found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        Optional<JSONArray> optTheirTeam = Util.getOptJSONArray(data, "theirTeam");
        if (!optTheirTeam.isPresent()) {
            log("No theirTeam found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return Optional.empty();
        }

        JSONObject timer = optTimer.get();
        JSONArray myTeam = optMyTeam.get();
        JSONArray theirTeam = optTheirTeam.get();

        JSONArray feMyTeam = new JSONArray();
        JSONArray feTheirTeam = new JSONArray();
        JSONObject feTimer = new JSONObject();

        Util.copyJsonAttributes(timer, feTimer, JSON_KEY_PHASE, "isInfinite");


        for (int i = 0, arrayLength = myTeam.length(); i < arrayLength; i++) {
            JSONObject member = myTeam.getJSONObject(i);
            feMyTeam.put(i, teamMemberToSessionMap(member, timer));
        }

        for (int i = 0, arrayLength = theirTeam.length(); i < arrayLength; i++) {
            JSONObject member = theirTeam.getJSONObject(i);
            feTheirTeam.put(i, teamMemberToSessionMap(member, timer));
        }

        frontendChampSelect.put("myTeam", feMyTeam);
        frontendChampSelect.put("theirTeam", feTheirTeam);
        frontendChampSelect.put("timer", feTimer);

        Util.copyJsonAttributes(timer, feTimer, JSON_KEY_PHASE, "isInfinite");

        return Optional.ofNullable(frontendChampSelect);
    }

    private JSONObject teamMemberToSessionMap(JSONObject feMember, JSONObject timer) {
        Integer cellId = feMember.getInt("cellId");
        JSONObject banAction = banMap.get(cellId);
        JSONObject pickAction = pickMap.get(cellId);

        if (banAction == null) {
            banAction = new JSONObject();
        }

        if (pickAction == null) {
            pickAction = new JSONObject();
        }

        feMember.put(JSON_KEY_BAN_ACTION, banAction);
        feMember.put(JSON_KEY_PICK_ACTION, pickAction);

        JSONObject phaseObject = new JSONObject();
        Util.copyJsonAttrib(JSON_KEY_PHASE, timer, phaseObject);
        phaseObject.put(JSON_KEY_BAN_ACTION, banAction);
        phaseObject.put(JSON_KEY_PICK_ACTION, pickAction);

        ChampSelectState state = ChampSelectState.fromParameters(phaseObject);
        if (state == null) {
            log(feMember);
            log(timer);
            log("No fitting state found for cellId: " + cellId);
        } else {

            feMember.put(JSON_KEY_STATE, state.name());
        }
        cellIdMemberMap.put(cellId, feMember);
        return feMember;
    }

    private void setCustomGameBans(JSONObject data, JSONObject frontendChampSelect) {
        //Bans already handled in array for each team
        //Actions are only added (at least ban/pick wise) when they actually happen
        Optional<JSONObject> optBans = Util.getOptJSONObject(data, "bans");
        if (!optBans.isPresent()) {
            log("No bans found in champ select session", MainInitiator.LOG_LEVEL.DEBUG);
            return;
        }

        JSONObject bans = optBans.get();

        frontendChampSelect.put("isCustomGame", true);
        frontendChampSelect.put("bans", bans);
    }

    private void updateActionMappings(JSONArray action) {
        if (action.isEmpty()) return;
        int actionLength = action.length();
        for (int i = 0; i < actionLength; i++) {
            JSONArray subAction = action.getJSONArray(i);
            if (subAction.isEmpty()) continue;
            int subActionLength = subAction.length();
            for (int j = 0; j < subActionLength; j++) {
                JSONObject actionObject = subAction.getJSONObject(j);
                updateSingleAction(actionObject);
            }
        }
    }

    private void updateSingleAction(JSONObject action) {
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

    private void setNormalGameBans(JSONObject data, JSONObject frontendChampSelect) {
        //Bans are NOT handled in array for each team
        //Actions are already all present
        frontendChampSelect.put("isCustomGame", false);

        JSONArray myTeamBans = new JSONArray();
        JSONArray theirTeamBans = new JSONArray();

        setBansFromBanMap(myTeamBans, theirTeamBans);

        JSONObject bans = new JSONObject();

        bans.put("myTeamBans", myTeamBans);
        bans.put("theirTeamBans", theirTeamBans);

        frontendChampSelect.put("bans", bans);;
    }

    private void setBansFromBanMap(JSONArray myTeamBans, JSONArray theirTeamBans) {
        for (JSONObject banAction : banMap.values()) {
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
                    myTeamBans.put(Integer.valueOf(-1));
                } else {
                    theirTeamBans.put(Integer.valueOf(-1));
                }
            }
            if (!isCompleted) continue;

            Optional<Integer> optChampionId = Util.getOptInt(banAction, "championId");
            if (!optChampionId.isPresent()) continue;
            Integer championId = optChampionId.get();

            if (isAllyAction) {
                myTeamBans.put(championId);
            } else {
                theirTeamBans.put(championId);
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
    protected Optional<JSONObject> fetchCurrentState() {
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-champ-select/v1/session");
        JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, con);
        if (!data.has("errorCode")) return Optional.of(data);
        log("Error while fetching current state: " + data.getString("message"), MainInitiator.LOG_LEVEL.ERROR);
        return Optional.empty();
    }

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(com.iambadatplaying.lcuHandler.DataManager.getEventDataString(UPDATE_TYPE_CHAMP_SELECT, currentState));
    }
}
