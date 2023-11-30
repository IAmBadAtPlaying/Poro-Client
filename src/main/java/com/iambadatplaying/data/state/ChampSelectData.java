package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.util.*;

public class ChampSelectData extends StateDataManager {

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
            boolean banExists = parameters.has(JSON_KEY_BAN_ACTION);
            boolean pickExists = parameters.has(JSON_KEY_PICK_ACTION);

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
    private Map<Integer, JSONObject> cellIdActionMap;
    private Map<Integer, JSONObject> banMap;

    public ChampSelectData(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    public void doInitialize() {
        cellIdMemberMap = Collections.synchronizedMap(new HashMap<>());
        cellIdActionMap = Collections.synchronizedMap(new HashMap<>());
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

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(com.iambadatplaying.lcuHandler.DataManager.getEventDataString(UPDATE_TYPE_CHAMP_SELECT, currentState));
    }

    public void resetSession() {
        currentState = null;
        cellIdMemberMap.clear();
        cellIdActionMap.clear();
    }

    protected Optional<JSONObject> fetchCurrentState() {
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-champ-select/v1/session");
        JSONObject data = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, con);
        if (!data.has("errorCode")) return Optional.of(data);
        log("Error while fetching current state: " + data.getString("message"), MainInitiator.LOG_LEVEL.ERROR);
        return Optional.empty();
    }

    private Optional<JSONObject> backendToFrontendChampSelectSession(JSONObject data) {
        JSONObject frontendChampSelect = new JSONObject();

        Util.copyJsonAttributes(data, frontendChampSelect, "isCustomGame", JSON_KEY_LOCAL_PLAYER_CELL_ID, "gameId", "hasSimultaneousBans", "skipChampionSelect", "benchEnabled", "rerollsRemaining", "actions");

        Optional<JSONArray> optActions = Util.getJSONArray(data, "actions");
        if (!optActions.isPresent()) return Optional.empty();
        JSONArray actions = optActions.get();
        updateInternalActionMappings(actions);

        JSONObject feTimer = new JSONObject();

        Optional<JSONObject> optTimer = Util.getJSONObject(data, "timer");
        if (!optTimer.isPresent()) {
            log("No timer found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }
        JSONObject timer = optTimer.get();

        Optional<Integer> optLocalPlayerCellId = Util.getInteger(data, JSON_KEY_LOCAL_PLAYER_CELL_ID);
        if (!optLocalPlayerCellId.isPresent()) {
            log("No localPlayerCellId found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }
        Integer localPlayerCellId = optLocalPlayerCellId.get();

        Optional<JSONArray> optMyTeam = Util.getJSONArray(data, "myTeam");
        if (!optMyTeam.isPresent()) {
            log("No myTeam found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        //Allied Team
        JSONArray feMyTeam = optMyTeam.get();
        for (int i = 0; i < feMyTeam.length(); i++) {
            int playerCellId = feMyTeam.getJSONObject(i).getInt("cellId");
            JSONObject playerObject = teamMemberToSessionMap(feMyTeam.getJSONObject(i), timer);
            if (playerCellId == localPlayerCellId) {
                frontendChampSelect.put("localPlayerPhase", playerObject.getString(JSON_KEY_STATE));
            }
            feMyTeam.put(i, playerObject);
        }
        frontendChampSelect.put("myTeam", feMyTeam);

        //Enemy Team
        Util.copyJsonAttributes(data, frontendChampSelect, "theirTeam");
        Optional<JSONArray> optTheirTeam = Util.getJSONArray(data, "theirTeam");
        if (!optTheirTeam.isPresent()) {
            log("No theirTeam found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JSONArray feTheirTeam = optTheirTeam.get();
        Util.copyJsonAttrib("theirTeam", data, frontendChampSelect);

        for (int i = 0; i < feTheirTeam.length(); i++) {
            log(feTheirTeam.getJSONObject(i), MainInitiator.LOG_LEVEL.INFO);
            feTheirTeam.put(i, enemyMemberToSessionMap(feTheirTeam.getJSONObject(i), timer));
        }

        Util.copyJsonAttributes(timer, feTimer, JSON_KEY_PHASE, "isInfinite");

        frontendChampSelect.put("timer", feTimer);

        // TODO: Test if bans are always present
        Optional<JSONObject> optBans = Util.getJSONObject(data, "bans");
        if (!optBans.isPresent()) {
            log("No bans found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JSONObject bans = optBans.get();
        JSONObject feBans = new JSONObject();

        Util.copyJsonAttributes(bans, feBans, "myTeamBans", "theirTeamBans", "numBans");

        frontendChampSelect.put("bans", feBans);

        return Optional.of(frontendChampSelect);
    }

    private void updateInternalActionMappings(JSONArray action) {
        if (action == null || action.isEmpty()) return;
        for (int i = 0; i < action.length(); i++) {
            JSONArray subAction = action.getJSONArray(i);
            if (subAction == null || subAction.isEmpty()) continue;
            for (int j = 0; j < subAction.length(); j++) {
                JSONObject singleAction = subAction.getJSONObject(j);
                updateSingleActionMapping(singleAction);
            }
        }
    }

    private void handleBanActions(JSONObject action) {
        if (action == null || action.isEmpty()) return;
        Optional<Integer> optActorCellId = Util.getInteger(action, "actorCellId");
        if (!optActorCellId.isPresent()) return;
        Integer actorCellId = optActorCellId.get();
        Optional<String> optType = Util.getString(action, "type");
        if (!optType.isPresent()) return;
        String type = optType.get();
        cellIdActionMap.compute(actorCellId, (k, v) -> {
            if (v == null || v.isEmpty()) {
                v = new JSONObject();
            }
            JSONObject currentAction = new JSONObject();
            currentAction.put("type", type);
            Util.copyJsonAttributes(action, currentAction, JSON_KEY_COMPLETED, JSON_KEY_IS_IN_PROGRESS, "championId", "id");
            switch (type) {
                case "ban":
                    v.put(JSON_KEY_BAN_ACTION, currentAction);
                    break;
                default:
                    log("Unknown action type: " + type, MainInitiator.LOG_LEVEL.ERROR);
                    break;
            }
            return v;
        });
    }

    //TODO Refactor with enemyMemberToSessionMap
    private JSONObject teamMemberToSessionMap(JSONObject feMember, JSONObject timer) {
        Integer cellId = feMember.getInt("cellId");
        JSONObject mappedAction = cellIdActionMap.get(cellId);
        if (mappedAction == null || mappedAction.isEmpty()) {
            log("No fitting action found for cellId: " + cellId);
        } else {
            Util.copyJsonAttributes(mappedAction, feMember, JSON_KEY_BAN_ACTION, JSON_KEY_PICK_ACTION);
        }

        JSONObject phaseObject = new JSONObject();
        Util.copyJsonAttrib(JSON_KEY_PHASE, timer, phaseObject);
        Util.copyJsonAttributes(mappedAction, phaseObject, JSON_KEY_PICK_ACTION, JSON_KEY_BAN_ACTION);

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

    private JSONObject enemyMemberToSessionMap(JSONObject feMember, JSONObject timer) {
        Integer cellId = feMember.getInt("cellId");
        JSONObject mappedAction = cellIdActionMap.get(cellId);
        if (mappedAction == null || mappedAction.isEmpty()) {
            log("ENEMY: No fitting action found for cellId: " + cellId);
        } else {
            Util.copyJsonAttributes(mappedAction, feMember, JSON_KEY_BAN_ACTION, JSON_KEY_PICK_ACTION);
        }

        JSONObject phaseObject = new JSONObject();
        Util.copyJsonAttrib(JSON_KEY_PHASE, timer, phaseObject);
        Util.copyJsonAttributes(mappedAction, phaseObject, JSON_KEY_PICK_ACTION, JSON_KEY_BAN_ACTION);

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

    private void updateSingleActionMapping(JSONObject singleAction) {
        if (singleAction.isEmpty()) return;
        Optional<Integer> optActorCellId = Util.getInteger(singleAction, "actorCellId");
        if (!optActorCellId.isPresent()) return;
        Integer actorCellId = optActorCellId.get();
        Optional<String> optType = Util.getString(singleAction, "type");
        if (!optType.isPresent()) return;
        String type = optType.get();
        cellIdActionMap.compute(actorCellId, (k, v) -> {
            if (v == null || v.isEmpty()) {
                v = new JSONObject();
            }
            JSONObject currentAction = new JSONObject();
            currentAction.put("type", type);
            Util.copyJsonAttributes(singleAction, currentAction, JSON_KEY_COMPLETED, JSON_KEY_IS_IN_PROGRESS, "championId", "id");
            switch (type) {
                case "pick":
                    v.put(JSON_KEY_PICK_ACTION, currentAction);
                    break;
                case "ban":
                    v.put(JSON_KEY_BAN_ACTION, currentAction);
                    break;
                default:
                    log("Unknown action type: " + type, MainInitiator.LOG_LEVEL.ERROR);
                    break;
            }
            return v;
        });
    }

    private int performChampionAction(String actionType, Integer championId, boolean lockIn) {
        if (currentState == null || currentState.isEmpty() || championId == null || championId < 0) {
            return -1;
        }

        if (!currentState.has(JSON_KEY_LOCAL_PLAYER_CELL_ID)) {
            return -1;
        }

        Integer localPlayerCellId = currentState.getInt(JSON_KEY_LOCAL_PLAYER_CELL_ID);
        JSONObject actionBundle = cellIdActionMap.get(localPlayerCellId);

        if (actionBundle == null || actionBundle.isEmpty()) {
            return -1;
        }

        JSONObject actionObject;

        if (actionType.equals("pick")) {
            actionObject = actionBundle.getJSONObject(JSON_KEY_PICK_ACTION);
        } else if (actionType.equals("ban")) {
            actionObject = actionBundle.getJSONObject(JSON_KEY_BAN_ACTION);
        } else {
            return -1;
        }

        int actionId = actionObject.getInt("id");
        JSONObject hoverAction = new JSONObject();
        hoverAction.put("championId", championId);

        if (lockIn) {
            if ("pick".equals(actionType)) {
                triggerPickLockInSound(championId);
            }
            hoverAction.put(JSON_KEY_COMPLETED, true);
        }

        try {
            String request = "/lol-champ-select/v1/session/actions/" + actionId;
            HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.PATCH, request, hoverAction.toString());
            return con.getResponseCode();
        } catch (Exception e) {
            log("Error while performing champion action: " + e.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
        }
        return -1;
    }

    private void triggerPickLockInSound(int championId) {
        JSONObject soundObject = new JSONObject();
        soundObject.put("source", "/lol-game-data/assets/v1/champion-sfx-audios/" + championId + ".ogg");
        mainInitiator.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(INSTRUCTION_PLAY_SOUND, soundObject));
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                JSONObject voiceLineObject = new JSONObject();
                voiceLineObject.put("source", "/lol-game-data/assets/v1/champion-choose-vo/" + championId + ".ogg");
                mainInitiator.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(INSTRUCTION_PLAY_SOUND, voiceLineObject));
            }
        }, 300);
    }


    public void doShutdown() {
        if (cellIdMemberMap != null) cellIdMemberMap.clear();
        cellIdMemberMap = null;
        if (cellIdActionMap != null) cellIdActionMap.clear();
        cellIdActionMap = null;

        currentState = null;
    }
}
