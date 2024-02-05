package com.iambadatplaying.data.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.BasicDataManager;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.lcuHandler.DataManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;

public class TickerMessageManager extends ArrayDataManager {

    private static final String URI = "/lol-service-status/v1/ticker-messages";

    private static final String UPDATE_TYPE_TICKER_MESSAGE = ReworkedDataManager.UPDATE_TYPE_TICKER_MESSAGES;

    public TickerMessageManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected boolean isRelevantURI(String uri) {
        return URI.equals(uri.trim());
    }

    @Override
    protected void doUpdateAndSend(String uri, String type, JsonElement data) {
        switch (type) {
            case BasicDataManager.UPDATE_TYPE_CREATE:
            case BasicDataManager.UPDATE_TYPE_UPDATE:
                if (!data.isJsonArray()) return;
                Optional<JsonArray> updatedState = fetchCurrentState();
                if (!updatedState.isPresent()) return;
                if (Util.equalJsonElements(updatedState.get(), array)) return;
                array = updatedState.get();
                sendCurrentState();
                break;
            case "Delete":
                resetState();
                break;
            default:
                log("Unknown type: " + type);
                break;
        }
    }

    @Override
    protected void doShutdown() {

    }

    @Override
    protected Optional<JsonArray> fetchCurrentState() {
        HttpsURLConnection con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-service-status/v1/ticker-messages");
        JsonArray data = ConnectionManager.getResponseBodyAsJsonArray(con);
        return Optional.of(data);
    }

    @Override
    public void sendCurrentState() {
        starter.getServer().sendToAllSessions(DataManager.getEventDataString(UPDATE_TYPE_TICKER_MESSAGE, array));
    }

    @Override
    public String getEventName() {
        return ReworkedDataManager.UPDATE_TYPE_TICKER_MESSAGES;
    }
}
