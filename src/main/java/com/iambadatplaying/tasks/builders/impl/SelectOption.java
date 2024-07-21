package com.iambadatplaying.tasks.builders.impl;

public class SelectOption {
    private String value;
    private String displayName;

    public SelectOption(
            String displayName,
            String value
    ) {
        this.value = value;
        this.displayName = displayName;
    }

    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }
}
