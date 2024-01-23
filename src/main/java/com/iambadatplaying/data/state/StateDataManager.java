package com.iambadatplaying.data.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.BasicDataManager;

import java.util.Optional;

public abstract class StateDataManager extends BasicDataManager {

    protected StateDataManager(Starter starter) {
        super(starter);
    }

    protected JsonObject currentState = null;

    public Optional<JsonObject> getCurrentState() {
        if (!initialized) {
            log("Not initialized, wont fetch current state", Starter.LOG_LEVEL.ERROR);
            return Optional.empty();
        }
        if (currentState != null) return Optional.of(currentState);
        Optional<JsonObject> newState = fetchCurrentState();
        newState.ifPresent(jsonObject -> currentState = jsonObject);
        return newState;
    }

    public void setCurrentState(JsonObject currentState) {
        this.currentState = currentState;
    }

    protected abstract Optional<JsonObject> fetchCurrentState();

    public abstract void sendCurrentState();

    public void updateState(String uri, String type, JsonElement data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }
        if (!isRelevantURI(uri)) return;
        doUpdateAndSend(uri, type, data);
    }

    public void resetState() {
        if (!initialized) {
            log("Not initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }
        currentState = new JsonObject();
    }
}
