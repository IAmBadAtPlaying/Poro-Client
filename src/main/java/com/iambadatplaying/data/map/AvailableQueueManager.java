package com.iambadatplaying.data.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.ConnectionManager;


public class AvailableQueueManager extends MapDataManager<Integer> {

    public AvailableQueueManager(Starter starter) {
        super(starter);
    }


    private static final String QUEUE_AVAILABLE = "Available";
    private static final String KEY_QUEUE_AVAILABILITY = "queueAvailability";

    @Override
    public JsonObject load(Integer key) {
        return new JsonObject();
    }

    @Override
    public void doInitialize() {
        fetchQueues();
    }

    private void fetchQueues() {
        JsonArray queueArray = starter.getConnectionManager().getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-game-queues/v1/queues"));
        for (int i = 0; i < queueArray.size(); i++) {
            JsonObject currentQueue = queueArray.get(i).getAsJsonObject();
            if (QUEUE_AVAILABLE.equals(currentQueue.get(KEY_QUEUE_AVAILABILITY).getAsString())) {
                Integer queueId = currentQueue.get("id").getAsInt();
                map.put(queueId, currentQueue);
            }
        }
    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return false;
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        //No known updates yet
    }

    @Override
    public void doShutdown() {
        map.clear();
        map = null;
    }
}
