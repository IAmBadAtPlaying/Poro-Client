package com.iambadatplaying.frontendHanlder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.data.state.*;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.TaskManager;

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
        JsonArray messageArray = null;
        if (message == null || message.isEmpty()) {
            return;
        }
        try {
            JsonElement messageElement = JsonParser.parseString(message);
            if (!messageElement.isJsonArray()) {
                return;
            }
            messageArray = messageElement.getAsJsonArray();
        } catch (Exception e) {
            return;
        }
        if (!messageArray.isEmpty()) { //[(Int) opcode, .., (Int) requestId]
            Integer opcode = messageArray.get(0).getAsInt();
            int len = messageArray.size();
            switch (opcode) {
                case 0: //Handled by Proxy
                    log(messageArray.toString(), MainInitiator.LOG_LEVEL.INFO);
                    log("Opcode 0 is no longer supported", MainInitiator.LOG_LEVEL.ERROR);
                    break;
                case 1: //Subscribe / Unsubscribe from LCU endpoints
                    if (len < 2) {
                        return;
                    }
                    JsonArray instructionArray = messageArray.get(1).getAsJsonArray();
                    int instruction = instructionArray.get(0).getAsInt();
                    switch (instruction) {
                        case 5:
                            mainInitiator.getClient().getSocket().subscribeToEndpoint(instructionArray.get(1).getAsString());
                            break;
                        case 6:
                            mainInitiator.getClient().getSocket().unsubscribeFromEndpoint(instructionArray.get(1).getAsString());
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
                    JsonElement echoElement = messageArray.get(1);
                    if (echoElement.isJsonArray() || echoElement.isJsonObject()) {
                        mainInitiator.getServer().sendToAllSessions(echoElement.toString());
                    }
                    break;
//                case 3: // This will be used to enable and disable Tasks
//                    if (len < 5) {
//                        return;
//                    }
//                    int operation = messageArray.get(1).getAsInt();
//                    String taskName = messageArray.get(2).getAsString();
//                    JsonObject taskArgs = messageArray.get(3).getAsJsonObject();
//                    TaskManager taskManager = mainInitiator.getTaskManager();
//                    switch (operation) {
//                        case 0:  //Delete Task
//                            taskManager.removeTask(taskName);
//                            break;
//                        case 1: //Create Task
//                        case 2: //Modify Task
//                            taskManager.addTask(taskName);
//                            Task task = taskManager.getActiveTaskByName(taskName);
//                            if (task != null) {
//                                System.out.println("Getting active Task: " + task.getClass().getSimpleName());
//                                task.setTaskArgs(taskArgs);
//                            }
//                            break;
//                        default:
//                            break;
//                    }
//                    break;
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
                    String requestTypeRiot = messageArray.get(1).getAsString();
                    String endpointRiot = messageArray.get(2).getAsString();
                    String bodyRiot = messageArray.get(3).getAsString();
                    Integer requestIdRiot = messageArray.get(4).getAsInt();
                    handleForwardRequest(requestTypeRiot, endpointRiot, bodyRiot, requestIdRiot, socket, true);
                    break;
//                case 6: //Disenchant Elements
//                    if (len < 3) {
//                        return;
//                    }
//                    JsonArray disenchantArray = messageArray.get(1).getAsJsonArray();
//                    mainInitiator.getDataManager().disenchantElements(disenchantArray);
//                    log("[ENDPOINT] Disenchanted Elements");
//                    break;
//                case 7: //Reroll Skins
//                    if (len < 3) {
//                        return;
//                    }
//                    JsonArray rerollArray = messageArray.get(1).getAsJsonArray();
//                    mainInitiator.getDataManager().rerollElements(rerollArray);
//                    log("[ENDPOINT] Rerolled Elements");
//                    break;
                case 10: //End the application
                    if (len < 3) {
                        return;
                    }
                    String shutdownOption = messageArray.get(1).getAsString();
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
        JsonArray tasks = mainInitiator.getTaskManager().getTaskAndArgs();
        socket.sendMessage(DataManager.getEventDataString("InitialTaskUpdate", tasks));
    }

    private void sendModifiedData(Socket socket) {
        JsonObject summonerSpells = mainInitiator.getDataManager().getSummonerSpellJson();
        socket.sendMessage(DataManager.getDataTransferString("SummonerSpells", summonerSpells));
        JsonObject skins = mainInitiator.getDataManager().getChromaSkinId();
        socket.sendMessage(DataManager.getDataTransferString("ChromaSkins", skins));
        JsonObject champions = mainInitiator.getDataManager().getChampionJson();
        socket.sendMessage(DataManager.getDataTransferString("Champions", champions));
    }

    private void sendLoot(Socket socket) {
        Optional<JsonObject> feGameflowObject = mainInitiator.getReworkedDataManager().getStateManagers(LootData.class).getCurrentState();
        JsonObject lootObject = new JsonObject();
        if (feGameflowObject.isPresent()) {
            lootObject = feGameflowObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialLoot", lootObject));
    }

    private void sendAvailableQueues(Socket socket) {
        JsonObject queues = mainInitiator.getDataManager().getAvailableQueues();
        socket.sendMessage(DataManager.getEventDataString("InitialQueues", queues));
    }

    private void sendChampSelect(Socket socket) {
        Optional<JsonObject> feChampSelectObject = mainInitiator.getReworkedDataManager().getStateManagers(ReworkedChampSelectData.class).getCurrentState();
        JsonObject champSelectObject = new JsonObject();
        if (feChampSelectObject.isPresent()) {
            champSelectObject = feChampSelectObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialChampSelectUpdate", champSelectObject));
    }

    private void sendFriendList(Socket socket) {
        JsonObject feFriendArray = mainInitiator.getDataManager().getFEFriendObject();
//            JSONObject feFriendArray = mainInitiator.getReworkedDataManager().getMapManagers(FriendManager.class.getSimpleName()).getMapAsJson();
        socket.sendMessage(DataManager.getEventDataString("InitialFriendListUpdate", feFriendArray));
    }

    private void sendGameflowStatus(Socket socket) {
        Optional<JsonObject> feGameflowObject = mainInitiator.getReworkedDataManager().getStateManagers(GameflowData.class).getCurrentState();
        JsonObject lobbyObject = new JsonObject();
        if (feGameflowObject.isPresent()) {
            lobbyObject = feGameflowObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialGameflowUpdate", lobbyObject));
    }

    private void sendLobby(Socket socket) {
        Optional<JsonObject> feLobbyObject = mainInitiator.getReworkedDataManager().getStateManagers(LobbyData.class).getCurrentState();
        JsonObject lobbyObject = new JsonObject();
        if (feLobbyObject.isPresent()) {
            lobbyObject = feLobbyObject.get();
        }
//            JSONObject lobbyObject = mainInitiator.getDataManager().getFELobbyObject();
        socket.sendMessage(DataManager.getEventDataString("InitialLobbyUpdate", lobbyObject));
    }

    private void sendSelfPresence(Socket socket) {
        Optional<JsonObject> fePresence = mainInitiator.getReworkedDataManager().getStateManagers(ChatMeManager.class).getCurrentState();
        JsonObject presence = new JsonObject();
        if (fePresence.isPresent()) {
            presence = fePresence.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialSelfPresenceUpdate", presence));
    }

    private void sendPatcher(Socket socket) {
        Optional<JsonObject> fePatcher = mainInitiator.getReworkedDataManager().getStateManagers(PatcherData.class).getCurrentState();
        JsonObject patcher = new JsonObject();
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
        JsonElement respElement = JsonParser.parseString(resp);
        JsonArray respArray = new JsonArray();
        respArray.add(requestNum);
        respArray.add(respElement);
        socket.sendMessage(respArray.toString());
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        log(s, MainInitiator.LOG_LEVEL.DEBUG);
    }
}
