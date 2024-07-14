package com.iambadatplaying.config.quickplayProfiles;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.ConfigLoader;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.config.ConfigModule;
import com.iambadatplaying.config.ConfigServlet;
import com.iambadatplaying.rest.filter.containerRequestFilters.ContainerRequestOriginFilter;
import com.iambadatplaying.rest.filter.containerRequestFilters.ContainerOptionsCorsFilter;
import com.iambadatplaying.rest.filter.containerResponseFilters.ContainerAllowOriginCorsFilter;
import com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyReader;
import com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
    public boolean loadConfiguration() {
        ConfigLoader configLoader = Starter.getInstance().getConfigLoader();
        if (configLoader == null) {
            return false;
        }

        Path userDataPath = configLoader.getUserDataFolderPath();

        if (userDataPath == null) {
            return false;
        }

        Path quickplayFolderPath = userDataPath.resolve(DIRECTORY).resolve(ConfigLoader.CONFIG_FILE_NAME);
        if (!quickplayFolderPath.toFile().exists()) {
            return false;
        }

        Optional<JsonElement> optJsonElement;
        try {
            optJsonElement = Util.parseJson(new String(Files.readAllBytes(quickplayFolderPath)));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return optJsonElement.filter(this::loadConfiguration).isPresent();

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
    public boolean saveConfiguration() {
        ConfigLoader configLoader = Starter.getInstance().getConfigLoader();
        if (configLoader == null) {
            return false;
        }

        Path userDataPath = configLoader.getUserDataFolderPath();

        if (userDataPath == null) {
            return false;
        }

        try {
            Path quickplayFolderPath = userDataPath.resolve(DIRECTORY).resolve(ConfigLoader.CONFIG_FILE_NAME);
            Files.write(quickplayFolderPath, new Gson().toJson(getConfiguration()).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

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

                ContainerRequestOriginFilter.class,
                ContainerOptionsCorsFilter.class,

                ContainerAllowOriginCorsFilter.class
        };
    }

    @Override
    public Class<? extends ConfigServlet> getRestServlet() {
        return QuickPlayServlet.class;
    }
}
