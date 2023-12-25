package com.iambadatplaying;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class ConfigLoader {

    private static final String LOCAL_FOLDER_PATH = System.getenv("LOCALAPPDATA");
    private final MainInitiator mainInitiator;

    private Path APP_FOLDER_PATH;
    private Path USER_DATA_FOLDER_PATH;

    public static final String CONFIG_FILE_NAME = "config.json";

    public static final String USER_DATA_FOLDER_NAME = "userdata";

    public static final String KEY_SCHEMA_VERSION = "schemaVersion";
    public static final Integer CURRENT_SCHEMA_VERSION = 1;

    // CLIENT PROPERTIES
    public static final String KEY_SECTION_CLIENT_PROPERTIES = "clientProperties";

    public static final String PROPERTY_CLIENT_BACKGROUND_TYPE = "clientBackgroundType";
    public static final String PROPERTY_CLIENT_BACKGROUND = "clientBackground";
    public static final String PROPERTY_CLIENT_BACKGROUND_CONTENT_TYPE = "clientBackgroundContentType";

    // QUICK-PLAY PROFILES
    public static final String KEY_SECTION_QUICK_PLAY_PROFILES = "quickPlayProfiles";

    public static final String PROPERTY_QUICK_PLAY_PROFILE_NAME = "name";
    public static final String PROPERTY_QUICK_PLAY_PROFILE_UUID = "uuid";
    public static final String PROPERTY_QUICK_PLAY_PROFILE_COMMENT = "comment";
    public static final String PROPERTY_QUICK_PLAY_PROFILE_SCHEMA_VERSION = "schemaVersion";
    public static final String PROPERTY_QUICK_PLAY_PROFILE_DATA = "data";

    private JsonObject config;

    public ConfigLoader(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
        this.runOnInitiation();
    }

    public enum CLIENT_BACKGROUND_TYPE {
        NONE,
        LOCAL_IMAGE,
        LOCAL_VIDEO,
        LCU_IMAGE,
        LCU_VIDEO;

        public static CLIENT_BACKGROUND_TYPE fromString(String s) {
            switch (s) {
                case "LOCAL_IMAGE":
                    return LOCAL_IMAGE;
                case "LOCAL_VIDEO":
                    return LOCAL_VIDEO;
                case "LCU_IMAGE":
                    return LCU_IMAGE;
                case "LCU_VIDEO":
                    return LCU_VIDEO;
                case "NONE":
                default:
                    return NONE;
            }
        }
    }

    public void loadConfig() {
        Path configPath = Paths.get(APP_FOLDER_PATH + File.separator + CONFIG_FILE_NAME);
        if (!Files.exists(configPath)) {
            try {
                Files.createFile(configPath);
                config = new JsonObject();
                setupDefaultConfig();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                log("Failed to create config file", MainInitiator.LOG_LEVEL.ERROR);
                return;
            }
        }
        try {
            JsonElement readConfig = Util.parseJson(new String(Files.readAllBytes(configPath)));
            config = readConfig.getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
            log("Failed to read config file", MainInitiator.LOG_LEVEL.ERROR);
            config = new JsonObject();
        }
    }

    private void setupDefaultConfig() {
        config.addProperty(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        config.add(KEY_SECTION_CLIENT_PROPERTIES, new JsonObject());
        config.add(KEY_SECTION_QUICK_PLAY_PROFILES, new JsonObject());
    }

    public void saveConfig() {
        log("Saving config file");
        Path configPath = Paths.get(APP_FOLDER_PATH + File.separator + CONFIG_FILE_NAME);
        try {
            Files.write(configPath, config.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            log("Failed to save config file", MainInitiator.LOG_LEVEL.ERROR);
        }
    }

    public JsonObject getClientProperties() {
        if (!config.has(KEY_SECTION_CLIENT_PROPERTIES)) {
            return new JsonObject();
        }
        return config.get(KEY_SECTION_CLIENT_PROPERTIES).getAsJsonObject();
    }

    public void setClientProperties(JsonObject clientProperties) {
        config.add(KEY_SECTION_CLIENT_PROPERTIES, clientProperties);
    }

    public void setClientProperty(String key, JsonElement value) {
        config.get(KEY_SECTION_CLIENT_PROPERTIES).getAsJsonObject().add(key, value);
    }

    public void setClientProperty(String key, String value) {
        config.get(KEY_SECTION_CLIENT_PROPERTIES).getAsJsonObject().addProperty(key, value);
    }

    private void runOnInitiation() {
        if (!setupLocalAppdataFolder()) {
            log("Failed to setup local appdata folder", MainInitiator.LOG_LEVEL.ERROR);
        }
        if (!setupUserDataFolder()) {
            log("Failed to setup user data folder", MainInitiator.LOG_LEVEL.ERROR);
        }
    }

    private boolean setupLocalAppdataFolder() {
        Path path = Paths.get(LOCAL_FOLDER_PATH + File.separator + MainInitiator.getAppDirName());
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        APP_FOLDER_PATH = path;
        return true;
    }

    private boolean setupUserDataFolder() {
        Path path = Paths.get(APP_FOLDER_PATH + File.separator + USER_DATA_FOLDER_NAME);
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        USER_DATA_FOLDER_PATH = path;
        return true;
    }

    public JsonObject getConfig() {
        return config;
    }

    public Path getAppFolderPath() {
        return APP_FOLDER_PATH;
    }

    public void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + s, level);
    }

    public void log(String s) {
        mainInitiator.log(this.getClass().getSimpleName() +": " +s);
    }
}
