package com.iambadatplaying.frontendHanlder;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.TaskManager;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

import static com.iambadatplaying.MainInitiator.log;

public class FrontendMessageHandler {

    private MainInitiator mainInitiator;

    public FrontendMessageHandler(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    /**
     *
     * @param message Message in form of a Stringified JSON array
     *
     */
    public void handleMessage(String message, Session session) {
        JSONArray messageArray = null;
        if (message == null || message.isEmpty()) {
            return;
        }
        try {
            messageArray = new JSONArray(message);
        } catch (Exception e) {
            return;
        }
        if (messageArray != null && !messageArray.isEmpty()) { //[(Int) opcode, .., (Int) requestId]
            Integer opcode = messageArray.getInt(0);
            if (opcode == null) opcode = -1;
            int len = messageArray.length();
            switch (opcode) {
                case 0: //Proxy the request
                    if (len <= 4) {
                        return;
                    }
                    String requestType = messageArray.getString(1);
                    String endpoint = messageArray.getString(2);
                    String body = messageArray.getString(3);
                    Integer requestId = messageArray.getInt(4);
                    handleForwardRequest(requestType, endpoint,body, requestId ,session, false);
                break;
                case 1: //Subscribe / Unsubscribe from LCU endpoints
                    if (len < 2) {
                        return;
                    }
                    JSONArray instructionArray = messageArray.getJSONArray(1);
                    Integer instruction = instructionArray.getInt(0);
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
                case 2: //We will use this as a kind of echo so we can emulate backend update Calls
                    if (len < 3) {
                        return; // We want this form [2, []]; With [] being the command we want to echo
                    }
                    JSONArray echoArray = null;
                    String toSend = null;
                    try  {
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
                    Integer operation =  messageArray.getInt(1);
                    String taskName = messageArray.getString(2);
                    JSONObject taskArgs = messageArray.getJSONObject(3);
                    TaskManager taskManager = mainInitiator.getTaskManager(); //TODO:
                    switch (operation) {
                        case 0:  //Delete Task
                            taskManager.removeTask(taskName);
                        break;
                        case 1: //Create Task
                        case 2: //Modify Task
                            taskManager.addTask(taskName);
                            Task task = taskManager.getActiveTaskByName(taskName);
                            if (task != null) {
                                System.out.println("Getting active Task: " +task.getClass().getSimpleName());
                                task.setTaskArgs(taskArgs);
                            }
                        break;
                        default:
                        break;
                    }
                break;
                case 4: // Startup Handler
                     if (len < 2) return;
                     sendFriendList(session);
                     sendGameflowStatus(session);
                     sendLobby(session);
                     sendAvailableQueues(session);
                     sendTasks(session);
                     sendLoot(session);
                     sendModifiedData(session);
                break;
                case 5: // Proxy the request to Riot-Client (NOT league client)
                    if (len <= 4) {
                        return;
                    }
                    String requestTypeRiot = messageArray.getString(1);
                    String endpointRiot = messageArray.getString(2);
                    String bodyRiot = messageArray.getString(3);
                    Integer requestIdRiot = messageArray.getInt(4);
                    handleForwardRequest(requestTypeRiot, endpointRiot,bodyRiot, requestIdRiot ,session, true);
                break;
                case 6: //Disenchant Elements
                    if (len < 3) {
                        return;
                    }
                    JSONArray disenchantArray = messageArray.getJSONArray(1);
                    mainInitiator.getDataManager().disenchantElements(disenchantArray);
                    log("[ENDPOINT] Disenchanted Elements");
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
                            mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/player-notifications/v1/notifications" , discBody));
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

    private void sendTasks(Session session) {
        try {
            JSONArray tasks = mainInitiator.getTaskManager().getTaskAndArgs();
            session.getRemote().sendString(DataManager.getEventDataString("InitialTaskUpdate", tasks));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendModifiedData(Session session) {
        try {
            JSONObject summonerSpells = mainInitiator.getDataManager().getSummonerSpellJson();
            session.getRemote().sendString(DataManager.getDataTransferString("SummonerSpells", summonerSpells));
            JSONObject skins = mainInitiator.getDataManager().getChromaSkinId();
            session.getRemote().sendString(DataManager.getDataTransferString("ChromaSkins", skins));
            JSONObject champions = mainInitiator.getDataManager().getChampionJson();
            session.getRemote().sendString(DataManager.getDataTransferString("Champions", champions));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendLoot(Session session) {
        try {
            JSONObject loot = mainInitiator.getDataManager().getLoot();
            session.getRemote().sendString(DataManager.getEventDataString("InitialLoot", loot));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendAvailableQueues(Session session) {
        try {
            JSONObject queues = mainInitiator.getDataManager().getAvailableQueues();
            session.getRemote().sendString(DataManager.getEventDataString("InitialQueues", queues));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendRiotData(Session session) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("Riot Basic Auth Header", mainInitiator.getConnectionManager().getRiotAuth());
            obj.put("Riot Port", mainInitiator.getConnectionManager().getRiotPort());
            session.getRemote().sendString(obj.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSummonerInfo(Session session) {
        try {
            JSONObject summonerInfo = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-summoner/v1/current-summoner"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendFriendList(Session session) {
        try {
            JSONObject feFriendArray = mainInitiator.getDataManager().getFEFriendObject();
            session.getRemote().sendString(DataManager.getEventDataString("InitialFriendListUpdate", feFriendArray));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendGameflowStatus(Session session) {
        try {
            String gameflowStatus = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-gameflow/v1/gameflow-phase"));
            JSONObject gameflowObject = mainInitiator.getDataManager().getFEGameflowStatus();
            session.getRemote().sendString(mainInitiator.getDataManager().getEventDataString("InitialGameflowUpdate", gameflowObject));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendLobby(Session session) {
        try {
            JSONObject feLobbyObject = mainInitiator.getDataManager().getFELobbyObject();
            session.getRemote().sendString(mainInitiator.getDataManager().getEventDataString("InitialLobbyUpdate", feLobbyObject));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleForwardRequest(String requestType, String endpoint, String body, Integer requestNum, Session session, boolean isRiotConnection) {
        ConnectionManager.conOptions type = ConnectionManager.conOptions.getByString(requestType);
        HttpsURLConnection con;
        if (isRiotConnection) {
            con = mainInitiator.getConnectionManager().buildRiotConnection(type,endpoint,body);
        } else con = mainInitiator.getConnectionManager().buildConnection(type, endpoint, body);

        String resp = (String) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, con);
        if (resp!= null && !resp.isEmpty()) {
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
        sendMessage(respArray.toString(), session);
    }



    public void sendMessage(String message, Session session) {
        try {
            session.getRemote().sendString(message);
        } catch (Exception e) {

        }
    }
}
