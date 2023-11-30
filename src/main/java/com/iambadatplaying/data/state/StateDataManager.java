package com.iambadatplaying.data.state;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.data.BasicDataManager;
import org.json.JSONObject;

import java.util.Optional;

public abstract class StateDataManager extends BasicDataManager {

    protected StateDataManager(MainInitiator mainInitiator) {
        super(mainInitiator);
    }

    protected JSONObject currentState = null;

    public Optional<JSONObject> getCurrentState() {
        if (!initialized) {
            log("Not initialized, wont fetch current state", MainInitiator.LOG_LEVEL.ERROR);
            return Optional.empty();
        }
        if (currentState != null) return Optional.of(currentState);
        Optional<JSONObject> newState = fetchCurrentState();
        newState.ifPresent(jsonObject -> currentState = jsonObject);
        return newState;
    }

    public void setCurrentState(JSONObject currentState) {
        this.currentState = currentState;
    }

    protected abstract Optional<JSONObject> fetchCurrentState();

    public abstract void sendCurrentState();

    public void updateState(String uri, String type,JSONObject data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", MainInitiator.LOG_LEVEL.WARN);
            return;
        }
        if (!isRelevantURI(uri)) return;
        doUpdateAndSend(uri, type, data);
    }

    public void resetState() {
        if (!initialized) {
            log("Not initialized, wont have any effect", MainInitiator.LOG_LEVEL.WARN);
            return;
        }
        currentState = new JSONObject();
    }
}
