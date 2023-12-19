package com.iambadatplaying.data.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

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
        public static ChampSelectState fromParameters(JsonObject parameters) {
            if (parameters == null) return null;
            String timerPhase = parameters.get(JSON_KEY_PHASE).getAsString();
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
    private Map<Integer, JsonObject> cellIdActionMap;

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
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case "Create":
            case "Update":
                if (data == null || !data.isJsonObject()) return;
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

    @Override
    public void sendCurrentState() {
        mainInitiator.getServer().sendToAllSessions(com.iambadatplaying.lcuHandler.DataManager.getEventDataString(UPDATE_TYPE_CHAMP_SELECT, currentState));
    }

    public void resetSession() {
        currentState = null;
        cellIdMemberMap.clear();
        cellIdActionMap.clear();
    }

    protected Optional<JsonObject> fetchCurrentState() {
        HttpsURLConnection con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-champ-select/v1/session");
        JsonObject data = mainInitiator.getConnectionManager().getResponseBodyAsJsonObject(con);
        if (!data.has("errorCode")) return Optional.of(data);
        log("Error while fetching current state: " + data.get("message").getAsString(), MainInitiator.LOG_LEVEL.ERROR);
        return Optional.empty();
    }

    private Optional<JsonObject> backendToFrontendChampSelectSession(JsonObject data) {
        JsonObject frontendChampSelect = new JsonObject();

        Util.copyJsonAttributes(data, frontendChampSelect, "isCustomGame", JSON_KEY_LOCAL_PLAYER_CELL_ID, "gameId", "hasSimultaneousBans", "skipChampionSelect", "benchEnabled", "rerollsRemaining", "actions");

        Optional<JsonArray> optActions = Util.getOptJSONArray(data, "actions");
        if (!optActions.isPresent()) return Optional.empty();
        JsonArray actions = optActions.get();
        updateInternalActionMappings(actions);

        JsonObject feTimer = new JsonObject();

        Optional<JsonObject> optTimer = Util.getOptJSONObject(data, "timer");
        if (!optTimer.isPresent()) {
            log("No timer found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }
        JsonObject timer = optTimer.get();

        Optional<Integer> optLocalPlayerCellId = Util.getOptInt(data, JSON_KEY_LOCAL_PLAYER_CELL_ID);
        if (!optLocalPlayerCellId.isPresent()) {
            log("No localPlayerCellId found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }
        Integer localPlayerCellId = optLocalPlayerCellId.get();

        Optional<JsonArray> optMyTeam = Util.getOptJSONArray(data, "myTeam");
        if (!optMyTeam.isPresent()) {
            log("No myTeam found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        // TODO: Test if bans are always present
        Optional<JsonObject> optBans = Util.getOptJSONObject(data, "bans");
        if (!optBans.isPresent()) {
            log("No bans found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonObject bans = optBans.get();
        JsonObject feBans = new JsonObject();

        Util.copyJsonAttributes(bans, feBans, "numBans");

        Optional<JsonArray> optMyTeamBans = Util.getOptJSONArray(bans, "myTeamBans");
        boolean myTeamBansEmpty = false;
        if (optMyTeamBans.isPresent()) {
            myTeamBansEmpty = optMyTeamBans.get().isEmpty();
            feBans.add("myTeamBans", optMyTeamBans.get());
        }

        Optional<JsonArray> optTheirTeamBans = Util.getOptJSONArray(bans, "theirTeamBans");
        boolean theirTeamBansEmpty = false;
        if (optTheirTeamBans.isPresent()) {
            theirTeamBansEmpty = optTheirTeamBans.get().isEmpty();
            feBans.add("theirTeamBans", optTheirTeamBans.get());
        }


        //Allied Team
        JsonArray feMyTeam = optMyTeam.get();
        for (int i = 0; i < feMyTeam.size(); i++) {
            JsonObject playerObject = teamMemberToSessionMap(feMyTeam.get(i).getAsJsonObject(), timer);
            if (myTeamBansEmpty && (playerObject.has(JSON_KEY_BAN_ACTION))) {
                    JsonObject banAction = playerObject.get(JSON_KEY_BAN_ACTION).getAsJsonObject();
                    if (banAction.has("championId") && banAction.has("completed") && (banAction.get("completed").getAsBoolean())) {
                            optMyTeamBans.get().add(banAction.get("championId").getAsInt());

                    }

            }
            feMyTeam.add(playerObject);
        }
        frontendChampSelect.add("myTeam", feMyTeam);

        //Enemy Team
        Util.copyJsonAttributes(data, frontendChampSelect, "theirTeam");
        Optional<JsonArray> optTheirTeam = Util.getOptJSONArray(data, "theirTeam");
        if (!optTheirTeam.isPresent()) {
            log("No theirTeam found", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }

        JsonArray feTheirTeam = optTheirTeam.get();
        Util.copyJsonAttrib("theirTeam", data, frontendChampSelect);

        for (int i = 0; i < feTheirTeam.size(); i++) {
            log(feTheirTeam.get(i).getAsJsonObject(), MainInitiator.LOG_LEVEL.INFO);
            JsonObject playerObject = enemyMemberToSessionMap(feTheirTeam.get(i).getAsJsonObject(), timer);
            feTheirTeam.add(playerObject);

            if (theirTeamBansEmpty && (playerObject.has(JSON_KEY_BAN_ACTION))) {
                JsonObject banAction = playerObject.get(JSON_KEY_BAN_ACTION).getAsJsonObject();
                if (banAction.has("championId") && banAction.has("completed") && (banAction.get("completed").getAsBoolean())) {
                    optMyTeamBans.get().add(banAction.get("championId").getAsInt());

                }
            }
        }

        feBans.add("theirTeamBans", optTheirTeamBans.get());
        feBans.add("myTeamBans", optMyTeamBans.get());

        Util.copyJsonAttributes(timer, feTimer, JSON_KEY_PHASE, "isInfinite");

        frontendChampSelect.add("timer", feTimer);



        return Optional.of(frontendChampSelect);
    }

    private void updateInternalActionMappings(JsonArray action) {
        if (action == null || action.isEmpty()) return;
        for (int i = 0; i < action.size(); i++) {
            JsonArray subAction = action.get(i).getAsJsonArray();
            if (subAction == null || subAction.isEmpty()) continue;
            for (int j = 0; j < subAction.size(); j++) {
                JsonObject singleAction = subAction.get(j).getAsJsonObject();
                updateSingleActionMapping(singleAction);
            }
        }
    }


    //TODO Refactor with enemyMemberToSessionMap
    private JsonObject teamMemberToSessionMap(JsonObject feMember, JsonObject timer) {
        Integer cellId = feMember.get("cellId").getAsInt();
        JsonObject mappedAction = cellIdActionMap.get(cellId);
        if (mappedAction == null || mappedAction.isEmpty()) {
            log("No fitting action found for cellId: " + cellId);
        } else {
            Util.copyJsonAttributes(mappedAction, feMember, JSON_KEY_BAN_ACTION, JSON_KEY_PICK_ACTION);
        }

        JsonObject phaseObject = new JsonObject();
        Util.copyJsonAttrib(JSON_KEY_PHASE, timer, phaseObject);
        Util.copyJsonAttributes(mappedAction, phaseObject, JSON_KEY_PICK_ACTION, JSON_KEY_BAN_ACTION);

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

    private JsonObject enemyMemberToSessionMap(JsonObject feMember, JsonObject timer) {
        Integer cellId = feMember.get("cellId").getAsInt();
        JsonObject mappedAction = cellIdActionMap.get(cellId);
        if (mappedAction == null || mappedAction.isEmpty()) {
            log("ENEMY: No fitting action found for cellId: " + cellId);
        } else {
            Util.copyJsonAttributes(mappedAction, feMember, JSON_KEY_BAN_ACTION, JSON_KEY_PICK_ACTION);
        }

        JsonObject phaseObject = new JsonObject();
        Util.copyJsonAttrib(JSON_KEY_PHASE, timer, phaseObject);
        Util.copyJsonAttributes(mappedAction, phaseObject, JSON_KEY_PICK_ACTION, JSON_KEY_BAN_ACTION);

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

    private void updateSingleActionMapping(JsonObject singleAction) {
        if (singleAction.isEmpty()) return;
        Optional<Integer> optActorCellId = Util.getOptInt(singleAction, "actorCellId");
        if (!optActorCellId.isPresent()) return;
        Integer actorCellId = optActorCellId.get();
        Optional<String> optType = Util.getOptString(singleAction, "type");
        if (!optType.isPresent()) return;
        String type = optType.get();
        cellIdActionMap.compute(actorCellId, (k, v) -> {
            if (v == null || v.isEmpty()) {
                v = new JsonObject();
            }
            JsonObject currentAction = new JsonObject();
            currentAction.addProperty("type", type);
            Util.copyJsonAttributes(singleAction, currentAction, JSON_KEY_COMPLETED, JSON_KEY_IS_IN_PROGRESS, "championId", "id");
            switch (type) {
                case "pick":
                    v.add(JSON_KEY_PICK_ACTION, currentAction);
                    break;
                case "ban":
                    v.add(JSON_KEY_BAN_ACTION, currentAction);
                    break;
                default:
                    log("Unknown action type: " + type, MainInitiator.LOG_LEVEL.ERROR);
                    break;
            }
            return v;
        });
    }


    public void doShutdown() {
        if (cellIdMemberMap != null) cellIdMemberMap.clear();
        cellIdMemberMap = null;
        if (cellIdActionMap != null) cellIdActionMap.clear();
        cellIdActionMap = null;

        currentState = null;
    }
}
