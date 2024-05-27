package com.iambadatplaying.frontendHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.ConnectionStatemachine;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.data.map.FriendGroupManager;
import com.iambadatplaying.data.map.FriendManager;
import com.iambadatplaying.data.map.QueueManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;

public class FrontendMessageHandler {

    private final Starter starter;

    public FrontendMessageHandler(Starter starter) {
        this.starter = starter;
    }


    public void sendInitialData(Socket socket) {
        sendFriendList(socket);
        sendFriendGroups(socket);
        sendAvailableQueues(socket);
        starter.getReworkedDataManager().sendInitialData(socket);
        sendTasks(socket);
    }

    public void sendFriendGroups(Socket socket) {
        JsonObject friendGroups = starter.getReworkedDataManager().getMapManagers(FriendGroupManager.class).getMapAsJson();
        socket.sendMessage(ReworkedDataManager.getInitialDataString(ReworkedDataManager.UPDATE_TYPE_FRIEND_GROUPS, friendGroups));
    }

    public void sendCurrentState(Socket socket) {
        ConnectionStatemachine csm = starter.getConnectionStatemachine();
        JsonObject newStateObject = new JsonObject();
        newStateObject.addProperty("state", csm.getCurrentState().name());
        socket.sendMessage(ReworkedDataManager.getInitialDataString(ReworkedDataManager.UPDATE_TYPE_INTERNAL_STATE, newStateObject));
    }

    private void sendTasks(Socket socket) {
        JsonArray tasks = starter.getTaskManager().getTaskAndArgs();
        socket.sendMessage(ReworkedDataManager.getEventDataString("InitialTaskUpdate", tasks));
    }

    private void sendAvailableQueues(Socket socket) {
        JsonObject queues = starter.getReworkedDataManager().getMapManagers(QueueManager.class).getMapAsJson();
        socket.sendMessage(ReworkedDataManager.getInitialDataString(ReworkedDataManager.UPDATE_TYPE_QUEUE, queues));
    }

    private void sendFriendList(Socket socket) {
        JsonObject feFriendArray = starter.getReworkedDataManager().getMapManagers(FriendManager.class).getMapAsJson();
        socket.sendMessage(ReworkedDataManager.getEventDataString("InitialFriendUpdate", feFriendArray));
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
