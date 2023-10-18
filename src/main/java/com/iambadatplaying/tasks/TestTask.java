package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import org.json.JSONArray;
import org.json.JSONObject;

public class TestTask extends Task {
    private String arg1 = "Unknown";

    public void notify(JSONArray webSocketEvent) {
        log(webSocketEvent.toString());
    }

    protected void doInitialize() {
    }

    public void doShutdown() {

    }

    public boolean setTaskArgs(JSONObject arguments) {
        try {
            arg1 = arguments.getString("arg1");
            log("Modified Task-Args for Task " + this.getClass().getSimpleName(), MainInitiator.LOG_LEVEL.DEBUG);
            return true;
        } catch (Exception e) {
            mainInitiator.getTaskManager().removeTask(this.getClass().getSimpleName());
        }
        return false;
    }

    public JSONObject getTaskArgs() {
        JSONObject taskArgs = new JSONObject();
        taskArgs.put("arg1", arg1);
        return taskArgs;
    }

    public JSONArray getRequiredArgs() {
        JSONArray requiredArgs = new JSONArray();

        JSONObject arg1 = new JSONObject();
        arg1.put("displayName", "arg1");
        arg1.put("required", true);
        arg1.put("type", "String");
        arg1.put("description", "This is the first argument");
        arg1.put("backendKey", "arg1");

        requiredArgs.put(arg1);

        return requiredArgs;
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }
}
