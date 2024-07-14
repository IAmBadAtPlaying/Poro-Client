package com.iambadatplaying.config.dynamicBackground;

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
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class BackgroundModule implements ConfigModule {

    public static final String REST_PATH = "background";
    public static final String DIRECTORY = "background";

    public static final String PROPERTY_BACKGROUND_TYPE = "backgroundType";
    public static final String PROPERTY_BACKGROUND = "background";
    public static final String PROPERTY_BACKGROUND_CONTENT_TYPE = "backgroundContentType";
    private CLIENT_BACKGROUND_TYPE backgroundType = CLIENT_BACKGROUND_TYPE.NONE;
    private String background = "";
    private String backgroundContentType = "";

    @Override
    public boolean loadConfiguration() {
        ConfigLoader configLoader = Starter.getInstance().getConfigLoader();
        if (configLoader == null) {
            System.out.println("ConfigLoader is null");
            return false;
        }

        Path userDataPath = configLoader.getUserDataFolderPath();

        if (userDataPath == null) {
            System.out.println("AppFolderPath is null");
            return false;
        }

        Path backgroundFolderPath = userDataPath.resolve(DIRECTORY).resolve(ConfigLoader.CONFIG_FILE_NAME);
        if (!backgroundFolderPath.toFile().exists()) {
            return false;
        }

        Optional<JsonElement> optJsonElement;
        try {
            optJsonElement = Util.parseJson(new String(Files.readAllBytes(backgroundFolderPath)));
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

        JsonObject jsonObject = config.getAsJsonObject();

        if (!Util.jsonKeysPresent(jsonObject, PROPERTY_BACKGROUND_TYPE, PROPERTY_BACKGROUND, PROPERTY_BACKGROUND_CONTENT_TYPE)) {
            return false;
        }

        backgroundType = CLIENT_BACKGROUND_TYPE.fromString(jsonObject.get(PROPERTY_BACKGROUND_TYPE).getAsString());
        background = jsonObject.get(PROPERTY_BACKGROUND).getAsString();
        backgroundContentType = jsonObject.get(PROPERTY_BACKGROUND_CONTENT_TYPE).getAsString();

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

        config.addProperty(PROPERTY_BACKGROUND_TYPE, backgroundType.toString());
        config.addProperty(PROPERTY_BACKGROUND, background);
        config.addProperty(PROPERTY_BACKGROUND_CONTENT_TYPE, backgroundContentType);

        return config;
    }

    @Override
    public String getRestPath() {
        return REST_PATH;
    }

    @Override
    public Class<?>[] getServletConfiguration() {
        return new Class[]{
                MultiPartFeature.class,
                GsonJsonElementMessageBodyReader.class,
                GsonJsonElementMessageBodyWriter.class,

                ContainerRequestOriginFilter.class,
                ContainerOptionsCorsFilter.class,

                ContainerAllowOriginCorsFilter.class
        };
    }

    @Override
    public Class<? extends ConfigServlet> getRestServlet() {
        return BackgroundServlet.class;
    }

    public Path getBackgroundPath() {
        return Starter.getInstance().getConfigLoader().getUserDataFolderPath().resolve(DIRECTORY).resolve(background);
    }

    public CLIENT_BACKGROUND_TYPE getBackgroundType() {
        return backgroundType;
    }

    public void setBackgroundType(CLIENT_BACKGROUND_TYPE backgroundType) {
        this.backgroundType = backgroundType;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getBackgroundContentType() {
        return backgroundContentType;
    }

    public void setBackgroundContentType(String backgroundContentType) {
        this.backgroundContentType = backgroundContentType;
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
}
