package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;

public class TestTask extends Task {
    private String arg1 = "Unknown";

    public void notify(JsonArray webSocketEvent) {
        log(webSocketEvent.toString());
    }

    protected void doInitialize() {
    }

    public void doShutdown() {

    }

    public boolean setTaskArgs(JsonObject arguments) {
        try {
            arg1 = arguments.get("arg1").getAsString();
            log("Modified Task-Args for Task " + this.getClass().getSimpleName(), Starter.LOG_LEVEL.DEBUG);
            return true;
        } catch (Exception e) {
            log("Failed to set Task-Args for Task " + this.getClass().getSimpleName(), Starter.LOG_LEVEL.ERROR);
        }
        return false;
    }

    public JsonObject getTaskArgs() {
        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("arg1", arg1);
        return taskArgs;
    }

    public JsonArray getRequiredArgs() {
        JsonArray requiredArgs = new JsonArray();

        JsonObject arg1 = new JsonObject();
        arg1.addProperty("displayName", "arg1");
        arg1.addProperty("required", true);
        arg1.addProperty("type", "String");
        arg1.addProperty("description", "This is the first argument");
        arg1.addProperty("backendKey", "arg1");

        requiredArgs.add(arg1);

        return requiredArgs;
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getName() + ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getName() + ": " + s);
    }
}
