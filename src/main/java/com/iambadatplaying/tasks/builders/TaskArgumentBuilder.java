package com.iambadatplaying.tasks.builders;

import com.google.gson.JsonObject;
import com.iambadatplaying.tasks.ARGUMENT_TYPE;
import com.iambadatplaying.tasks.Task;

public class TaskArgumentBuilder {
    private JsonObject taskArgs;

    public TaskArgumentBuilder() {
        taskArgs = new JsonObject();
    }

    public TaskArgumentBuilder setDisplayName(String displayName) {
        taskArgs.addProperty(Task.KEY_TASK_ARGUMENT_DISPLAY_NAME, displayName);
        return this;
    }

    public TaskArgumentBuilder setBackendKey(String key) {
        taskArgs.addProperty(Task.KEY_TASK_ARGUMENT_BACKEND_KEY, key);
        return this;
    }

    public TaskArgumentBuilder setType(ARGUMENT_TYPE type) {
        taskArgs.addProperty(Task.KEY_TASK_ARGUMENT_TYPE, type.toString());
        return this;
    }

    public TaskArgumentBuilder setRequired(boolean required) {
        taskArgs.addProperty(Task.KEY_TASK_ARGUMENT_REQUIRED, required);
        return this;
    }

    public TaskArgumentBuilder setCurrentValue(Object value) {
        if (value == null) {
            return this;
        }
        taskArgs.addProperty(Task.KEY_TASK_ARGUMENT_CURRENT_VALUE, value.toString());
        return this;
    }

    public TaskArgumentBuilder setDescription(String description) {
        taskArgs.addProperty(Task.KEY_TASK_ARGUMENT_DESCRIPTION, description);
        return this;
    }

    public TaskArgumentBuilder setAdditionalData(JsonObject additionalData) {
        taskArgs.add(Task.KEY_TASKS_ADDITIONAL_DATA, additionalData);
        return this;
    }

    public JsonObject build() {
        return taskArgs;
    }
}
