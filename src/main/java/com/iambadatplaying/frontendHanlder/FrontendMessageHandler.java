package com.iambadatplaying.frontendHanlder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.state.*;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;

public class FrontendMessageHandler {

    private final Starter starter;

    public FrontendMessageHandler(Starter starter) {
        this.starter = starter;
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
                case 2: //We will use this as a kind of echo, so we can emulate backend update Calls
                    if (len < 2) {
                        return; // We want this form [2, []]; With [] being the command we want to echo
                    }
                    JsonElement echoElement = messageArray.get(1);
                    if (echoElement.isJsonArray() || echoElement.isJsonObject()) {
                        starter.getServer().sendToAllSessions(echoElement.toString());
                    }
                    break;
                case 4: // Startup Handler
                    if (len < 1) return;
                    sendInitialData(socket);
                    break;
                case 5: // Proxy the request to Riot-Client (NOT league client)
                    if (len <= 3) {
                        return;
                    }
                    String requestTypeRiot = messageArray.get(1).getAsString();
                    String endpointRiot = messageArray.get(2).getAsString();
                    String bodyRiot = messageArray.get(3).getAsString();
                    Integer requestIdRiot = messageArray.get(4).getAsInt();
                    handleForwardRequest(requestTypeRiot, endpointRiot, bodyRiot, requestIdRiot, socket, true);
                    break;
                default:
                    log("Unknown Opcode: " + opcode + "; Context: " + messageArray, Starter.LOG_LEVEL.ERROR);
                    break;
            }
        }
    }

    public void sendInitialData(Socket socket) {
        sendFriendList(socket);
        starter.getReworkedDataManager().sendInitialData(socket);
        sendAvailableQueues(socket);
        sendTasks(socket);
        sendLoot(socket);
        sendModifiedData(socket);
    }

    private void sendTasks(Socket socket) {
        JsonArray tasks = starter.getTaskManager().getTaskAndArgs();
        socket.sendMessage(DataManager.getEventDataString("InitialTaskUpdate", tasks));
    }

    private void sendModifiedData(Socket socket) {
        JsonObject summonerSpells = starter.getDataManager().getSummonerSpellJson();
        socket.sendMessage(DataManager.getDataTransferString("SummonerSpells", summonerSpells));
        JsonObject skins = starter.getDataManager().getChromaSkinId();
        socket.sendMessage(DataManager.getDataTransferString("ChromaSkins", skins));
        JsonObject champions = starter.getDataManager().getChampionJson();
        socket.sendMessage(DataManager.getDataTransferString("Champions", champions));
    }

    private void sendLoot(Socket socket) {
        Optional<JsonObject> feGameflowObject = starter.getReworkedDataManager().getStateManagers(LootData.class).getCurrentState();
        JsonObject lootObject = new JsonObject();
        if (feGameflowObject.isPresent()) {
            lootObject = feGameflowObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialLoot", lootObject));
    }

    private void sendAvailableQueues(Socket socket) {
        JsonObject queues = starter.getDataManager().getAvailableQueues();
        socket.sendMessage(DataManager.getEventDataString("InitialQueues", queues));
    }

    private void sendChampSelect(Socket socket) {
        Optional<JsonObject> feChampSelectObject = starter.getReworkedDataManager().getStateManagers(ReworkedChampSelectData.class).getCurrentState();
        JsonObject champSelectObject = new JsonObject();
        if (feChampSelectObject.isPresent()) {
            champSelectObject = feChampSelectObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialChampSelectUpdate", champSelectObject));
    }

    private void sendFriendList(Socket socket) {
        JsonObject feFriendArray = starter.getDataManager().getFEFriendObject();
//            JSONObject feFriendArray = mainInitiator.getReworkedDataManager().getMapManagers(FriendManager.class.getSimpleName()).getMapAsJson();
        socket.sendMessage(DataManager.getEventDataString("InitialFriendListUpdate", feFriendArray));
    }

    private void sendGameflowStatus(Socket socket) {
        Optional<JsonObject> feGameflowObject = starter.getReworkedDataManager().getStateManagers(GameflowData.class).getCurrentState();
        JsonObject lobbyObject = new JsonObject();
        if (feGameflowObject.isPresent()) {
            lobbyObject = feGameflowObject.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialGameflowUpdate", lobbyObject));
    }

    private void sendLobby(Socket socket) {
        Optional<JsonObject> feLobbyObject = starter.getReworkedDataManager().getStateManagers(LobbyData.class).getCurrentState();
        JsonObject lobbyObject = new JsonObject();
        if (feLobbyObject.isPresent()) {
            lobbyObject = feLobbyObject.get();
        }
//            JSONObject lobbyObject = mainInitiator.getDataManager().getFELobbyObject();
        socket.sendMessage(DataManager.getEventDataString("InitialLobbyUpdate", lobbyObject));
    }

    private void sendSelfPresence(Socket socket) {
        Optional<JsonObject> fePresence = starter.getReworkedDataManager().getStateManagers(ChatMeManager.class).getCurrentState();
        JsonObject presence = new JsonObject();
        if (fePresence.isPresent()) {
            presence = fePresence.get();
        }
        socket.sendMessage(DataManager.getEventDataString("InitialSelfPresenceUpdate", presence));
    }

    private void sendPatcher(Socket socket) {
        Optional<JsonObject> fePatcher = starter.getReworkedDataManager().getStateManagers(PatcherData.class).getCurrentState();
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
            con = starter.getConnectionManager().buildRiotConnection(type, endpoint, body);
        } else con = starter.getConnectionManager().buildConnection(type, endpoint, body);

        String resp = (String) starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, con);
        if (resp != null && !resp.isEmpty()) {
            resp = resp.trim();
        }
        JsonElement respElement = JsonParser.parseString(resp);
        JsonArray respArray = new JsonArray();
        respArray.add(requestNum);
        respArray.add(respElement);
        socket.sendMessage(respArray.toString());
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        log(s, Starter.LOG_LEVEL.DEBUG);
    }
}
