package com.iambadatplaying.tasks.builders.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.tasks.builders.AdditionalDataBuilder;

public class SelectDataBuilder extends AdditionalDataBuilder {
    protected static final String KEY_SELECT_OPTIONS = "options";

    protected static final String KEY_SELECT_OPTION_VALUE = "value";
    protected static final String KEY_SELECT_OPTION_DISPLAY_NAME = "displayName";

    public SelectDataBuilder() {
        super();
    }

    public SelectDataBuilder addOptions(SelectOption[] options) {
        JsonArray optionsArray = new JsonArray();
        for (SelectOption option : options) {
            JsonObject optionObject = new JsonObject();
            optionObject.addProperty(KEY_SELECT_OPTION_VALUE, option.getValue());
            optionObject.addProperty(KEY_SELECT_OPTION_DISPLAY_NAME, option.getDisplayName());
            optionsArray.add(optionObject);
        }
        additionalData.add(KEY_SELECT_OPTIONS, optionsArray);
        return this;
    }
}
