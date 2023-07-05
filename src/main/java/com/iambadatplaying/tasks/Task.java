package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import org.json.JSONArray;
import org.json.JSONObject;

public interface Task {

    /**
     *
     * @param webSocketEvent The websocket Event JsonArray emitted by one of the TriggerApiEvents
     */
    public void notify(JSONArray webSocketEvent);


    /**
     *
     * @return The ApiEvents the Task should react on
     */
    public String[] getTriggerApiEvents();

    /**
     *
     *  This should set the MainInitiator if not already set in Constructor. MUST be called before {@link #init()}
     *
     */

    public void setMainInitiator(MainInitiator mainInitiator);

    /**
     *
     * This should initialize instances and variables needed.
     *
     */
    public void init();

    /**
     *
     * This should reset / set all instances and variables to null / delete them
     *
     */
    public void shutdown();


    /**
     *
     * @param arguments The arguments in json format to be passed and parsed
     */
    public void setTaskArgs(JSONObject arguments);
}
