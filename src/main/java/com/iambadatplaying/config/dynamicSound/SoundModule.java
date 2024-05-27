package com.iambadatplaying.config.dynamicSound;

import com.google.gson.JsonElement;
import com.iambadatplaying.config.ConfigModule;

public class SoundModule implements ConfigModule {
    public static final String KEY_GAMEFLOW_INQUEUE_SOUND_MAP = "GAMEFLOW_INQUEUE";

    @Override
    public boolean loadConfiguration(JsonElement config) {
        return false;
    }

    @Override
    public boolean loadStandardConfiguration() {
        return false;
    }

    @Override
    public boolean setupDirectories() {
        return false;
    }

    @Override
    public JsonElement getConfiguration() {
        return null;
    }

    @Override
    public String getRestPath() {
        return null;
    }

    @Override
    public Class<?>[] getServletConfiguration() {
        return new Class[0];
    }

    @Override
    public Class<?> getRestServlet() {
        return null;
    }
}
