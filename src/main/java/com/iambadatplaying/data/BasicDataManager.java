package com.iambadatplaying.data;

import com.iambadatplaying.MainInitiator;
import org.json.JSONObject;

public abstract class BasicDataManager {
    protected boolean initialized = false;
    protected MainInitiator mainInitiator;

    private BasicDataManager() {}

    protected BasicDataManager(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        log("Initialized", MainInitiator.LOG_LEVEL.INFO);
        doInitialize();
    }

    protected abstract void doInitialize();

    protected abstract boolean isRelevantURI(String uri);

    protected abstract void doUpdateAndSend(String uri, String type, JSONObject data);

    public void shutdown() {
        if (!initialized) return;
        initialized = false;
        doShutdown();
    }

    protected abstract void doShutdown();

    protected void log(Object o, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + o, level);
    }

    protected void log(Object o) {
        log(o, MainInitiator.LOG_LEVEL.DEBUG);
    }
}
