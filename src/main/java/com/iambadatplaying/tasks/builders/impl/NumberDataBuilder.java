package com.iambadatplaying.tasks.builders.impl;

import com.iambadatplaying.tasks.builders.AdditionalDataBuilder;

public class NumberDataBuilder extends AdditionalDataBuilder {
    private static final int DEFAULT_MINIMUM = Integer.MIN_VALUE;
    private static final int DEFAULT_MAXIMUM = Integer.MAX_VALUE;

    private static final String KEY_NUMBER_MINIMUM = "minimum";
    private static final String KEY_NUMBER_MAXIMUM = "maximum";

    public NumberDataBuilder() {
        super();
        additionalData.addProperty(KEY_NUMBER_MINIMUM, DEFAULT_MINIMUM);
        additionalData.addProperty(KEY_NUMBER_MAXIMUM, DEFAULT_MAXIMUM);
    }

    public NumberDataBuilder setMaximumValue(int maximum) {
        additionalData.addProperty(KEY_NUMBER_MAXIMUM, maximum);
        return this;
    }

    public NumberDataBuilder setMinimumValue(int minimum) {
        additionalData.addProperty(KEY_NUMBER_MINIMUM, minimum);
        return this;
    }
}
