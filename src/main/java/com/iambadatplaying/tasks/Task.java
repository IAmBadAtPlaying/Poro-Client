package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;

import java.util.Optional;


/**
 * @author IAmBadAtPlaying
 */
public abstract class Task {

    protected Starter starter;
    protected boolean running = false;

    public static final String KEY_TASK_NAME        = "name";
    public static final String KEY_TASK_ARGUMENTS   = "arguments";
    public static final String KEY_TASK_RUNNING     = "running";
    public static final String KEY_TASK_DESCRIPTION = "description";

    public static final String KEY_TASK_ARGUMENT_DISPLAY_NAME = "displayName";
    public static final String KEY_TASK_ARGUMENT_BACKEND_KEY  = "backendKey";
    public static final String KEY_TASK_ARGUMENT_TYPE         = "type";
    public static final String KEY_TASK_ARGUMENT_REQUIRED     = "required";
    public static final String KEY_TASK_ARGUMENT_CURRENT_VALUE = "currentValue";
    public static final String KEY_TASK_ARGUMENT_DESCRIPTION  = "description";

    public static final String KEY_TASKS_ADDITIONAL_DATA = "additionalData";


    private static final String DEFAULT_DESCRIPTION = "No description available";

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

    public final void init() {
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
    public final void shutdown() {
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

    public String getDescription() {
        return DEFAULT_DESCRIPTION;
    }

    /**
     * @return Whether the task is running or not
     */
    public boolean isRunning() {
        return this.running;
    }

    protected final void log(String s, Starter.LOG_LEVEL level) {
        Optional.ofNullable(starter).ifPresent(starter -> starter.log(this.getClass().getName() + ": " + s, level));
    }

    protected final void log(String s) {
        Optional.ofNullable(starter).ifPresent(starter -> starter.log(this.getClass().getName() + ": " + s));
    }
}
