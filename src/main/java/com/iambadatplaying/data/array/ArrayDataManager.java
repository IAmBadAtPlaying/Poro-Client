package com.iambadatplaying.data.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.iambadatplaying.Starter;
import com.iambadatplaying.data.BasicDataManager;

import java.util.Optional;

public abstract class ArrayDataManager extends BasicDataManager {

    protected JsonArray array = null;

    protected ArrayDataManager(Starter starter) {
        super(starter);
    }

    public Optional<JsonArray> getCurrentState() {
        if (!initialized) {
            log("Not initialized, wont fetch current state", Starter.LOG_LEVEL.ERROR);
            return Optional.empty();
        }
        if (array != null) return Optional.of(array);
        Optional<JsonArray> newState = fetchCurrentState();
        newState.ifPresent(jsonArray -> array = jsonArray);
        return newState;
    }

    public void setCurrentState(JsonArray currentState) {
        this.array = currentState;
    }

    protected abstract Optional<JsonArray> fetchCurrentState();

    public abstract void sendCurrentState();

    public void resetState() {
        if (!initialized) {
            log("Not initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }
        array = new JsonArray();
    }

    public abstract String getEventName();
}
