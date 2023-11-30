package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 *
 * @author IAmBadAtPlaying
 *
 * */
public abstract class Task {

    public enum INPUT_TYPE {
        TEXT,
        COLOR,
        CHECKBOX,
        NUMBER,
        CHAMPION_SELECT, //TODO: Implement
        SELECT;
    }

    protected MainInitiator mainInitiator;

    protected volatile boolean running = false;

    /**
     *
     * @param webSocketEvent The websocket Event JsonArray emitted by one of the TriggerApiEvents
     */
    public abstract void notify(JSONArray webSocketEvent);

    /**
     *
     *  This should set the MainInitiator if not already set in Constructor. MUST be called before {@link #init()}
     *
     */

    public void setMainInitiator(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    /**
     *
     * This will call {@link #doInitialize()} if the MainInitiator is set and running. If not it returns.
     *
     */

    public void init() {
        if (mainInitiator == null || !mainInitiator.isRunning()) {
            System.out.println("Task cant initialize, MainInitiator is null or not running");
        }
        doInitialize();
        this.running = true;
    }

    /**
     *
     * This will initialize the task. Called internally by {@link #init()}
     *
     */
    protected abstract void doInitialize();

    /**
     *
     * This will set running to false, call {@link #doShutdown()} and then set the mainInitiator to null.
     *
     */
    public void shutdown() {
        this.running = false;
        doShutdown();
        this.mainInitiator = null;
    }

    /**
     *
     * This will shutdown the task. Used internally.
     * @see #shutdown()
     *
     * */
    protected abstract void doShutdown();


    /**
     *
     * @param arguments The arguments in json format to be passed and saved
     */
    public abstract boolean setTaskArgs(JSONObject arguments);


    /**
     *
     * @return The current, via {@link #setTaskArgs(JSONObject)} set, arguments
     */
    public abstract JSONObject getTaskArgs();

    /**
     *
     * @return The arguments required and their format
     */
    public abstract JSONArray getRequiredArgs();


    /**
     *
     * @return Whether the task is running or not
     */
    public boolean isRunning() {
        return this.running;
    }
}
