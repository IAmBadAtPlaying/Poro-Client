package com.iambadatplaying.data;

import com.google.gson.JsonElement;
import com.iambadatplaying.Starter;

public abstract class BasicDataManager {
    protected boolean initialized = false;
    protected Starter starter;

    private BasicDataManager() {}

    protected BasicDataManager(Starter starter) {
        this.starter = starter;
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        doInitialize();
        log("Initialized", Starter.LOG_LEVEL.INFO);
    }

    protected abstract void doInitialize();

    protected abstract boolean isRelevantURI(String uri);

    protected abstract void doUpdateAndSend(String uri, String type, JsonElement data);

    public void shutdown() {
        if (!initialized) return;
        initialized = false;
        doShutdown();
    }

    protected abstract void doShutdown();

    protected void log(Object o, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() +": " + o, level);
    }

    protected void log(Object o) {
        log(o, Starter.LOG_LEVEL.DEBUG);
    }
}
