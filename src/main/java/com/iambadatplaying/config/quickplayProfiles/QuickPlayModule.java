package com.iambadatplaying.config.quickplayProfiles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.config.ConfigModule;
import com.iambadatplaying.rest.filter.OptionsCorsFilter;
import com.iambadatplaying.rest.filter.OriginFilter;
import com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyReader;
import com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyWriter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class QuickPlayModule implements ConfigModule {

    public static final String REST_PATH = "quickplay";
    public static final String DIRECTORY = "quickplay";

    public static final String PROPERTY_QUICKPLAY_PROFILES = "quickplayProfiles";

    public static final String PROPERTY_QUICKPLAY_PROFILE_NAME = "name";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOTS = "slots";

    public static final int PROPERTY_QUICKPLAY_PROFILE_SLOT_AMOUNT = 2;

    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_CHAMPION_ID = "championId";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS = "perks";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_POSITION_PREFERENCE = "positionPreference";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_SKIN_ID = "skinId";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL1 = "spell1";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_SPELL2 = "spell2";

    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_IDS = "perkIds";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_STYLE = "perkStyle";
    public static final String PROPERTY_QUICKPLAY_PROFILE_SLOT_PERKS_PERK_SUBSTYLE = "perkSubStyle";

    private final HashMap<String, JsonObject> quickplayProfiles;

    public QuickPlayModule() {
        quickplayProfiles = new HashMap<>();
    }


    public Map<String, JsonObject> getQuickplayProfiles() {
        return quickplayProfiles;
    }


    @Override
    public boolean loadConfiguration(JsonElement config) {
        if (!config.isJsonObject()) {
            return false;
        }

        JsonObject configObject = config.getAsJsonObject();

        if (!Util.jsonKeysPresent(configObject, PROPERTY_QUICKPLAY_PROFILES)) {
            return false;
        }


        JsonElement quickplayProfilesElement = configObject.get(PROPERTY_QUICKPLAY_PROFILES);
        if (!quickplayProfilesElement.isJsonObject()) {
            return false;
        }

        JsonObject quickplayProfilesObject = quickplayProfilesElement.getAsJsonObject();
        quickplayProfilesObject.entrySet().forEach(entry -> {
                    String key = entry.getKey();
                    JsonElement value = entry.getValue();

                    if (!value.isJsonObject()) {
                        return;
                    }

                    JsonObject profileJson = value.getAsJsonObject();

                    if (!Util.jsonKeysPresent(profileJson, PROPERTY_QUICKPLAY_PROFILE_NAME, PROPERTY_QUICKPLAY_PROFILE_SLOTS)) {
                        return;
                    }

                    quickplayProfiles.put(key, profileJson);
                }
        );

        return true;
    }

    @Override
    public boolean loadStandardConfiguration() {
        return true;
    }

    @Override
    public boolean setupDirectories() {
        Path modulePath = Starter.getInstance().getConfigLoader().getUserDataFolderPath().resolve(DIRECTORY);
        if (!modulePath.toFile().exists()) {
            return modulePath.toFile().mkdirs();
        }

        return true;
    }


    @Override
    public JsonElement getConfiguration() {
        JsonObject config = new JsonObject();

        JsonObject quickplayProfilesObject = new JsonObject();
        quickplayProfiles.forEach(quickplayProfilesObject::add);

        config.add(PROPERTY_QUICKPLAY_PROFILES, quickplayProfilesObject);

        return config;
    }

    @Override
    public String getRestPath() {
        return REST_PATH;
    }

    @Override
    public Class<?>[] getServletConfiguration() {
        return new Class[]{
                GsonJsonElementMessageBodyReader.class,
                GsonJsonElementMessageBodyWriter.class,

                OptionsCorsFilter.class,
                OriginFilter.class
        };
    }

    @Override
    public Class<?> getRestServlet() {
        return QuickPlayServlet.class;
    }
}
