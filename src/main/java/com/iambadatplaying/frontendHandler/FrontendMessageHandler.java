package com.iambadatplaying.frontendHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.ConnectionStatemachine;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.DataManager;
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
        sendOptionalMapManagerStates(socket);
        starter.getDataManager().sendInitialDataBlocking(socket);
        sendInitialUpdatesDone(socket);
    }

    private void sendOptionalMapManagerStates(Socket socket) {
        sendFriendList(socket);
        sendFriendGroups(socket);
        sendAvailableQueues(socket);
    }

    public void sendFriendGroups(Socket socket) {
        JsonObject friendGroups = starter.getDataManager().getMapManagers(FriendGroupManager.class).getMapAsJson();
        socket.sendMessage(DataManager.getInitialDataString(DataManager.UPDATE_TYPE_FRIEND_GROUPS, friendGroups));
    }

    public void sendCurrentState(Socket socket) {
        ConnectionStatemachine csm = starter.getConnectionStatemachine();
        JsonObject newStateObject = new JsonObject();
        newStateObject.addProperty("state", csm.getCurrentState().name());
        socket.sendMessage(DataManager.getInitialDataString(DataManager.UPDATE_TYPE_INTERNAL_STATE, newStateObject));
    }

    private void sendInitialUpdatesDone(Socket socket) {
        JsonObject data = new JsonObject();
        data.addProperty("done", true);
        socket.sendMessage(DataManager.getEventDataString(DataManager.UPDATE_TYPE_ALL_INITIAL_DATA_LOADED, data));
    }

    private void sendTasks(Socket socket) {
        JsonArray tasks = starter.getTaskManager().getTaskAndArgs();
        socket.sendMessage(DataManager.getEventDataString("InitialTaskUpdate", tasks));
    }

    private void sendAvailableQueues(Socket socket) {
        JsonObject queues = starter.getDataManager().getMapManagers(QueueManager.class).getMapAsJson();
        socket.sendMessage(DataManager.getInitialDataString(DataManager.UPDATE_TYPE_QUEUE, queues));
    }

    private void sendFriendList(Socket socket) {
        JsonObject feFriendArray = starter.getDataManager().getMapManagers(FriendManager.class).getMapAsJson();
        socket.sendMessage(DataManager.getEventDataString("InitialFriendUpdate", feFriendArray));
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        log(s, Starter.LOG_LEVEL.DEBUG);
    }
}
