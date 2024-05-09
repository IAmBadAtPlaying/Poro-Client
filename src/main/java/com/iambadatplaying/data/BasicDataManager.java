package com.iambadatplaying.data;

import com.google.gson.JsonElement;
import com.iambadatplaying.Starter;

public abstract class BasicDataManager {
    protected boolean initialized = false;
    protected Starter starter;

    protected static final String UPDATE_TYPE_DELETE = "Delete";
    protected static final String UPDATE_TYPE_CREATE = "Create";
    protected static final String UPDATE_TYPE_UPDATE = "Update";

    protected BasicDataManager(Starter starter) {
        this.starter = starter;
    }

    public void init() {
        if (initialized) return;
        doInitialize();
        initialized = true;
        log("Initialized", Starter.LOG_LEVEL.INFO);
    }

    protected abstract void doInitialize();

    protected abstract boolean isRelevantURI(String uri);

    protected abstract void doUpdateAndSend(String uri, String type, JsonElement data);

    public void update(String uri, String type, JsonElement data) {
        if (!initialized) {
            log("Not initialized, wont have any effect", Starter.LOG_LEVEL.WARN);
            return;
        }
        if (!isRelevantURI(uri)) return;
        doUpdateAndSend(uri, type, data);
    }
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
