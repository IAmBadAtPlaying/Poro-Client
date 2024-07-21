package com.iambadatplaying.tasks.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.tasks.ARGUMENT_TYPE;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.builders.TaskArgumentBuilder;

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

        requiredArgs.add(
                new TaskArgumentBuilder()
                        .setDisplayName("arg1")
                        .setBackendKey("arg1")
                        .setType(ARGUMENT_TYPE.TEXT)
                        .setRequired(true)
                        .setDescription("This is the first argument")
                        .build()
        );

        return requiredArgs;
    }
}
