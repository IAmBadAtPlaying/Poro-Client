package com.iambadatplaying.data.map;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;

public class AvailableQueueManager extends MapDataManager<Integer> {

    public AvailableQueueManager(MainInitiator mainInitiator) {
        super(mainInitiator);
    }


    private static final String QUEUE_AVAILABLE = "Available";
    private static final String KEY_QUEUE_AVAILABILITY = "queueAvailability";

    @Override
    public JSONObject load(Integer key) {
        return new JSONObject();
    }

    @Override
    public void doInitialize() {
        fetchQueues();
    }

    private void fetchQueues() {
        JSONArray queueArray = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-queues/v1/queues"));
        for (int i = 0; i < queueArray.length(); i++) {
            JSONObject currentQueue = queueArray.getJSONObject(i);
            if (QUEUE_AVAILABLE.equals(currentQueue.getString(KEY_QUEUE_AVAILABILITY))) {
                Integer queueId = currentQueue.getInt("id");
                map.put(queueId, currentQueue);
            }
        }
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return false;
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JSONObject data) {
        //No known updates yet
    }

    @Override
    public void doShutdown() {
        map.clear();
        map = null;
    }
}
