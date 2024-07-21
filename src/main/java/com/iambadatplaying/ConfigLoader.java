package com.iambadatplaying;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.config.ConfigModule;
import com.iambadatplaying.config.dynamicBackground.BackgroundModule;
import com.iambadatplaying.config.quickplayProfiles.QuickPlayModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConfigLoader {

    public static final String CONFIG_FILE_NAME = "config.json";
    public static final String USER_DATA_FOLDER_NAME = "userdata";
    public static final String TASKS_FOLDER_NAME = "tasks";
    public static final String BACKGROUNDS_FOLDER_NAME = "backgrounds";
    public static final String KEY_SCHEMA_VERSION = "schemaVersion";
    public static final Integer CURRENT_SCHEMA_VERSION = 1;

    private static final String LOCAL_FOLDER_PATH = System.getenv("LOCALAPPDATA");
    private final Starter starter;
    private Path APP_FOLDER_PATH;
    private Path USER_DATA_FOLDER_PATH;
    private JsonObject config;

    private final Map<String, ConfigModule> configModules;

    public ConfigLoader(Starter starter) {
        this.starter = starter;
        this.configModules = new HashMap<>();
        registerConfigModules();
        this.runOnInitiation();
    }

    private void registerConfigModules() {
        registerConfigModule(new BackgroundModule());
        registerConfigModule(new QuickPlayModule());
    }

    private void registerConfigModule(ConfigModule module) {
        configModules.put(module.getClass().getSimpleName(), module);
    }

    public void loadConfig() {
        Path configPath = Paths.get(APP_FOLDER_PATH.toString(), CONFIG_FILE_NAME);
        if (!Files.exists(configPath)) {
            try {
                Files.createFile(configPath);
                config = new JsonObject();
                setupDefaultConfig();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                log("Failed to create config file", Starter.LOG_LEVEL.ERROR);
                return;
            }
        }
        Optional<JsonElement> optReadConfig = Optional.empty();
        try {
            optReadConfig = Util.parseJson(new String(Files.readAllBytes(configPath)));
        } catch (IOException ignored) {
        }

        if (!optReadConfig.isPresent()) {
            log("Failed to read config file", Starter.LOG_LEVEL.ERROR);
            config = new JsonObject();
            handleCorruptedOrEmptyConfig();
            return;
        }

        JsonElement readConfig = optReadConfig.get();

        if (!readConfig.isJsonObject()) {
            log("Config file is not a JSON object", Starter.LOG_LEVEL.ERROR);
            config = new JsonObject();
            handleCorruptedOrEmptyConfig();
            return;
        }

        config = readConfig.getAsJsonObject();
        if (config.isEmpty()) {
            handleCorruptedOrEmptyConfig();
        }

        for (ConfigModule module : configModules.values()) {
            module.setupDirectories();

            if (!module.loadConfiguration()) {
                log("Failed to load configuration for " + module.getClass().getSimpleName() + ", using standard Configuration", Starter.LOG_LEVEL.ERROR);
                module.loadStandardConfiguration();
            }
        }
    }

    private void handleCorruptedOrEmptyConfig() {
        log("Config file is corrupted, resetting to default", Starter.LOG_LEVEL.ERROR);
        setupDefaultConfig();
    }

    private void setupDefaultConfig() {
        config.addProperty(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        for (ConfigModule module : configModules.values()) {
            module.setupDirectories();

            module.loadStandardConfiguration();
            JsonElement moduleConfig = module.getConfiguration();
            if (moduleConfig != null) {
                config.add(module.getClass().getSimpleName(), moduleConfig);
            }
        }
    }

    public void saveConfig() {
        log("Saving config file");
        for (ConfigModule module : configModules.values()) {
            if (!module.saveConfiguration()) {
                log("Failed to save configuration for " + module.getClass().getSimpleName(), Starter.LOG_LEVEL.ERROR);
            }
        }
        Path configPath = Paths.get(APP_FOLDER_PATH.toString(), CONFIG_FILE_NAME);
        try {
            Files.write(configPath, config.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            log("Failed to save config file", Starter.LOG_LEVEL.ERROR);
        }
    }


    private void runOnInitiation() {
        if (!setupLocalAppdataFolder()) {
            log("Failed to setup local appdata folder", Starter.LOG_LEVEL.ERROR);
        }
    }

    private boolean setupLocalAppdataFolder() {
        Path path = Paths.get(LOCAL_FOLDER_PATH + File.separator + Starter.getAppDirName());
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        APP_FOLDER_PATH = path;

        Path userDataPath = Paths.get(APP_FOLDER_PATH.toString(), USER_DATA_FOLDER_NAME);
        if (!Files.exists(userDataPath)) {
            try {
                Files.createDirectory(userDataPath);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        USER_DATA_FOLDER_PATH = userDataPath;
        return true;
    }

    public JsonObject getConfig() {
        return config;
    }

    public Path getUserDataFolderPath() {
        return USER_DATA_FOLDER_PATH;
    }

    public Path getAppFolderPath() {
        return APP_FOLDER_PATH;
    }

    public void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    public void log(String s) {
        starter.log(this.getClass().getSimpleName() + ": " + s);
    }

    public ConfigModule getConfigModule(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return configModules.get(clazz.getSimpleName());
    }

    public ConfigModule[] getConfigModules() {
        return configModules.values().toArray(new ConfigModule[0]);
    }
}
