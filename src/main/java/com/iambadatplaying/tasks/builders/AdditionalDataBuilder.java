package com.iambadatplaying.tasks.builders;

import com.google.gson.JsonObject;

public abstract class AdditionalDataBuilder {
    protected JsonObject additionalData;

    protected AdditionalDataBuilder() {
        additionalData = new JsonObject();
    }

    public JsonObject build() {
        return additionalData;
    }
}