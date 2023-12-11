package com.iambadatplaying.frontendHanlder;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.data.state.*;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.TaskManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;

public class FrontendMessageHandler {

    private final MainInitiator mainInitiator;

    public FrontendMessageHandler(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    /**
     * @param message Message in form of a Stringified JSON array
     */
    public void handleMessage(String message, Socket socket) {
        JSONArray messageArray = null;
        if (message == null || message.isEmpty()) {
            return;
        }
        try {
            messageArray = new JSONArray(message);
        } catch (Exception e) {
            return;
        }
        if (!messageArray.isEmpty()) { //[(Int) opcode, .., (Int) requestId]
            Integer opcode = messageArray.getInt(0);
            int len = messageArray.length();
            switch (opcode) {
                case 0: //Handled by Proxy
                    log(messageArray.toString(), MainInitiator.LOG_LEVEL.INFO);
                    log("Opcode 0 is no longer supported", MainInitiator.LOG_LEVEL.ERROR);
                    break;
                case 1: //Subscribe / Unsubscribe from LCU endpoints
                    if (len < 2) {
                        return;
                    }
                    JSONArray instructionArray = messageArray.getJSONArray(1);
                    int instruction = instructionArray.getInt(0);
                    switch (instruction) {
                        case 5:
                            mainInitiator.getClient().getSocket().subscribeToEndpoint(instructionArray.getString(1));
                            break;
                        case 6:
                            mainInitiator.getClient().getSocket().unsubscribeFromEndpoint(instructionArray.getString(1));
                            break;
                        default:
                            System.out.println("Error");
                            break;
                    }

                    break;
                case 2: //We will use this as a kind of echo, so we can emulate backend update Calls
                    if (len < 3) {
                        return; // We want this form [2, []]; With [] being the command we want to echo
                    }
                    JSONArray echoArray = null;
                    String toSend = null;
                    try {
                        echoArray = messageArray.getJSONArray(1);
                    } catch (Exception e) {

                    }
                    if (echoArray == null || echoArray.isEmpty()) {
                        JSONObject echoObject = null;
                        try {
                            echoObject = messageArray.getJSONObject(1);
                        } catch (Exception e) {

                        }
                        if (echoObject == null || echoObject.isEmpty()) {
                            return;
                        } else toSend = echoObject.toString();
                    } else toSend = echoArray.toString();
                    try {
                        mainInitiator.getServer().sendToAllSessions(toSend);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case 3: // This will be used to enable and disable Tasks
                    if (len < 5) {
                        return;
                    }
                    int operation = messageArray.getInt(1);
                    String taskName = messageArray.getString(2);
                    JSONObject taskArgs = messageArray.getJSONObject(3);
                    TaskManager taskManager = mainInitiator.getTaskManager();
                    switch (operation) {
                        case 0:  //Delete Task
                            taskManager.removeTask(taskName);
                            break;
                        case 1: //Create Task
                        case 2: //Modify Task
                            taskManager.addTask(taskName);
                            Task task = taskManager.getActiveTaskByName(taskName);
                            if (task != null) {
                                System.out.println("Getting active Task: " + task.getClass().getSimpleName());
                                task.setTaskArgs(taskArgs);
                            }
                            break;
                        default:
                            break;
                    }
                    break;
                case 4: // Startup Handler
                    if (len < 2) return;
                    sendFriendList(socket);
                    sendGameflowStatus(socket);
                    sendLobby(socket);
                    sendAvailableQueues(socket);
                    sendChampSelect(socket);
                    sendTasks(socket);
                    sendLoot(socket);
                    sendSelfPresence(socket);
                    sendPatcher(socket);
                    sendModifiedData(socket);
                    break;
                case 5: // Proxy the request to Riot-Client (NOT league client)
                    if (len <= 4) {
                        return;
                    }
                    String requestTypeRiot = messageArray.getString(1);
                    String endpointRiot = messageArray.getString(2);
                    String bodyRiot = messageArray.getString(3);
                    Integer requestIdRiot = messageArray.getInt(4);
                    handleForwardRequest(requestTypeRiot, endpointRiot, bodyRiot, requestIdRiot, socket, true);
                    break;
                case 6: //Disenchant Elements
                    if (len < 3) {
                        return;
                    }
                    JSONArray disenchantArray = messageArray.getJSONArray(1);
                    mainInitiator.getDataManager().disenchantElements(disenchantArray);
                    log("[ENDPOINT] Disenchanted Elements");
                    break;
                case 7: //Reroll Skins
                    if (len < 3) {
                        return;
                    }
                    JSONArray rerollArray = messageArray.getJSONArray(1);
                    mainInitiator.getDataManager().rerollElements(rerollArray);
                    log("[ENDPOINT] Rerolled Elements");
                    break;
                case 10: //End the application
                    if (len < 3) {
                        return;
                    }
                    String shutdownOption = messageArray.getString(1);
                    switch (shutdownOption) {
                        case "shutdown-all":
                            log("[Shutdown] Invoking League Client Shutdown", MainInitiator.LOG_LEVEL.INFO);
                            mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/process-control/v1/process/quit", ""));
                            log("[Shutdown] Invoking Self-shutdown", MainInitiator.LOG_LEVEL.INFO);
                            mainInitiator.shutdown();
                            break;
                        case "shutdown":
                        default:
                            String discBody = "{\"data\": {\"title\": \"Poro Client disconnected!\", \"details\": \"Poro-Client shutdown successful\" }, \"critical\": false, \"detailKey\": \"pre_translated_details\",\"backgroundUrl\" : \"https://cdn.discordapp.com/attachments/313713209314115584/1067507653028364418/Test_2.01.png\",\"iconUrl\": \"/fe/lol-settings/poro_smile.png\", \"titleKey\": \"pre_translated_title\"}";
                            mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/player-notifications/v1/notifications", discBody));
                            //Show Riot UX again so the user doesn't end up with league still running and them not noticing
                            mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/riotclient/launch-ux", ""));
                            log("[Shutdown] Invoking Self-shutdown", MainInitiator.LOG_LEVEL.INFO);
                            mainInitiator.shutdown();
                            break;
                    }
                default:
                    log("Unknown Opcode: " + opcode + "; Context: " + messageArray, MainInitiator.LOG_LEVEL.ERROR);
                    break;
            }
        }
    }

    private void sendTasks(Socket socket) {
        JSONArray tasks = mainInitiator.getTaskManager().getTaskAndArgs();
        socket.sendMessage(DataManager.getEventDataString("InitialTaskUpdate", tasks));
    }

    private void sendModifiedData(Socket socket) {
        JSONObject summonerSpells = mainInitiator.getDataManager().getSummonerSpellJson();
        socket.sendMessage(DataManager.getDataTransferString("SummonerSpells", summonerSpells));
        JSONObject skins = mainInitiator.getDataManager().getChromaSkinId();
        socket.sendMessage(DataManager.getDataTransferString("ChromaSkins", skins));
        JSONObject champions = mainInitiator.getDataManager().getChampionJson();
        socket.sendMessage(DataManager.getDataTransferString("Champions", champions));
    }

    private void sendLoot(Socket socket) {
        Optional<JSONObject> feGameflowObject = mainInitiator.getReworkedDataManager().getStateManagers(LootData.class.getSimpleName()).getCurrentState();
        JSONObject lootObject = new JSONObject();
        if (feGameflowObject.isPresent()) {
            lootObject = feGameflowObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialLoot", lootObject));
    }

    private void sendAvailableQueues(Socket socket) {
        JSONObject queues = mainInitiator.getDataManager().getAvailableQueues();
        socket.sendMessage(DataManager.getEventDataString("InitialQueues", queues));
    }

    private void sendChampSelect(Socket socket) {
        Optional<JSONObject> feChampSelectObject = mainInitiator.getReworkedDataManager().getStateManagers(ReworkedChampSelectData.class.getSimpleName()).getCurrentState();
        JSONObject champSelectObject = new JSONObject();
        if (feChampSelectObject.isPresent()) {
            champSelectObject = feChampSelectObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialChampSelectUpdate", champSelectObject));
    }

    private void sendFriendList(Socket socket) {
        JSONObject feFriendArray = mainInitiator.getDataManager().getFEFriendObject();
//            JSONObject feFriendArray = mainInitiator.getReworkedDataManager().getMapManagers(FriendManager.class.getSimpleName()).getMapAsJson();
        socket.sendMessage(DataManager.getEventDataString("InitialFriendListUpdate", feFriendArray));
    }

    private void sendGameflowStatus(Socket socket) {
        Optional<JSONObject> feGameflowObject = mainInitiator.getReworkedDataManager().getStateManagers(GameflowData.class.getSimpleName()).getCurrentState();
        JSONObject lobbyObject = new JSONObject();
        if (feGameflowObject.isPresent()) {
            lobbyObject = feGameflowObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialGameflowUpdate", lobbyObject));
    }

    private void sendLobby(Socket socket) {
        Optional<JSONObject> feLobbyObject = mainInitiator.getReworkedDataManager().getStateManagers(LobbyData.class.getSimpleName()).getCurrentState();
        JSONObject lobbyObject = new JSONObject();
        if (feLobbyObject.isPresent()) {
            lobbyObject = feLobbyObject.get();
        }
//            JSONObject lobbyObject = mainInitiator.getDataManager().getFELobbyObject();
        socket.sendMessage(DataManager.getEventDataString("InitialLobbyUpdate", lobbyObject));
    }

    private void sendSelfPresence(Socket socket) {
        Optional<JSONObject> fePresence = mainInitiator.getReworkedDataManager().getStateManagers(ChatMeManager.class.getSimpleName()).getCurrentState();
        JSONObject presence = new JSONObject();
        if (fePresence.isPresent()) {
            presence = fePresence.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialSelfPresenceUpdate", presence));
    }

    private void sendPatcher(Socket socket) {
        Optional<JSONObject> fePatcher = mainInitiator.getReworkedDataManager().getStateManagers(PatcherData.class.getSimpleName()).getCurrentState();
        JSONObject patcher = new JSONObject();
        if (fePatcher.isPresent()) {
            patcher = fePatcher.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialPatcher", patcher));
    }

    private void handleForwardRequest(String requestType, String endpoint, String body, Integer requestNum, Socket socket, boolean isRiotConnection) {
        ConnectionManager.conOptions type = ConnectionManager.conOptions.getByString(requestType);
        HttpsURLConnection con;
        if (isRiotConnection) {
            con = mainInitiator.getConnectionManager().buildRiotConnection(type, endpoint, body);
        } else con = mainInitiator.getConnectionManager().buildConnection(type, endpoint, body);

        String resp = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, con);
        if (resp != null && !resp.isEmpty()) {
            resp = resp.trim();
        }
        JSONObject respObject = null;
        JSONArray respArrayObj = null;
        try {
            respObject = new JSONObject(resp);
        } catch (Exception e) {
            try {
                respArrayObj = new JSONArray(resp);
            } catch (Exception ex) {
            }
        }
        JSONArray respArray = new JSONArray();
        respArray.put(requestNum);
        if (respObject != null) {
            respArray.put(respObject);
        } else if (respArrayObj != null) {
            respArray.put(respArrayObj);
        } else respArray.put(resp);
        socket.sendMessage(respArray.toString());
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        log(s, MainInitiator.LOG_LEVEL.DEBUG);
    }
}
