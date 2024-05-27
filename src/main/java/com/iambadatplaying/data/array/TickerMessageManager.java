package com.iambadatplaying.data.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.BasicDataManager;
import com.iambadatplaying.data.ReworkedDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TickerMessageManager extends ArrayDataManager {

    private static final Pattern TICKER_MESSAGES_PATTERN = Pattern.compile("/lol-service-status/v1/ticker-messages$");

    private static final String UPDATE_TYPE_TICKER_MESSAGE = ReworkedDataManager.UPDATE_TYPE_TICKER_MESSAGES;

    public TickerMessageManager(Starter starter) {
        super(starter);
    }

    @Override
    protected void doInitialize() {

    }

    @Override
    protected Matcher getURIMatcher(String uri) {
        return TICKER_MESSAGES_PATTERN.matcher(uri);
    }

    @Override
    protected void doUpdateAndSend(Matcher uriMatcher, String type, JsonElement data) {
        switch (type) {
            case BasicDataManager.UPDATE_TYPE_CREATE:
            case BasicDataManager.UPDATE_TYPE_UPDATE:
                if (!data.isJsonArray()) return;
                Optional<JsonArray> updatedState = fetchCurrentState();
                if (!updatedState.isPresent()) return;
                if (Util.equalJsonElements(updatedState.get(), currentArray)) return;
                currentArray = updatedState.get();
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
        starter.getServer().sendToAllSessions(ReworkedDataManager.getEventDataString(UPDATE_TYPE_TICKER_MESSAGE, currentArray));
    }

    @Override
    public String getEventName() {
        return ReworkedDataManager.UPDATE_TYPE_TICKER_MESSAGES;
    }
}
