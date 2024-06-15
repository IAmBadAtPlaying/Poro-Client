package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;


/**
 * @author IAmBadAtPlaying
 */
public abstract class Task {

    protected Starter starter;
    protected volatile boolean running = false;

    /**
     * @param webSocketEvent The websocket Event JsonArray emitted by one of the TriggerApiEvents
     */
    public abstract void notify(JsonArray webSocketEvent);

    /**
     * This should set the MainInitiator if not already set in Constructor. MUST be called before {@link #init()}
     */

    public void setMainInitiator(Starter starter) {
        this.starter = starter;
    }

    /**
     * This will call {@link #doInitialize()} if the MainInitiator is set and running. If not it returns.
     */

    public void init() {
        if (starter == null || !starter.isInitialized()) {
            System.out.println("Task cant initialize, MainInitiator is null or not running");
        }
        doInitialize();
        this.running = true;
    }

    /**
     * This will initialize the task. Called internally by {@link #init()}
     */
    protected abstract void doInitialize();

    /**
     * This will set running to false, call {@link #doShutdown()} and then set the mainInitiator to null.
     */
    public void shutdown() {
        this.running = false;
        doShutdown();
        this.starter = null;
    }

    /**
     * This will shutdown the task. Used internally.
     *
     * @see #shutdown()
     */
    protected abstract void doShutdown();

    /**
     * @param arguments The arguments in json format to be passed and saved
     */
    public abstract boolean setTaskArgs(JsonObject arguments);

    /**
     * @return The current, via {@link #setTaskArgs(JsonObject)} set, arguments
     */
    public abstract JsonObject getTaskArgs();

    /**
     * @return The arguments required and their format
     */
    public abstract JsonArray getRequiredArgs();

    /**
     * @return Whether the task is running or not
     */
    public boolean isRunning() {
        return this.running;
    }


    public enum INPUT_TYPE {
        TEXT,
        COLOR,
        CHECKBOX,
        NUMBER,
        CHAMPION_SELECT, //TODO: Implement
        SELECT
    }
}
