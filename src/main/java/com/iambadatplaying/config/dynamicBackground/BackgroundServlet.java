package com.iambadatplaying.config.dynamicBackground;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.config.ConfigModule;
import com.iambadatplaying.config.ConfigServlet;
import com.iambadatplaying.rest.servlets.ServletUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.*;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

@Path("/")
public class BackgroundServlet implements ConfigServlet {


    @GET
    public Response getBackground() {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(BackgroundModule.class);
        if (configModule == null) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .build();
        }

        BackgroundModule backgroundModule = (BackgroundModule) configModule;

        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        cacheControl.setNoStore(true);
        cacheControl.setMaxAge(0);
        cacheControl.setMustRevalidate(true);

        switch (backgroundModule.getBackgroundType()) {
            case NONE:
                return Response.status(Response.Status.NOT_FOUND).build();
            case LOCAL_IMAGE:
            case LOCAL_VIDEO:
                File backgroundFile = backgroundModule.getBackgroundPath().toFile();
                String contentType = backgroundModule.getBackgroundContentType();
                if (!backgroundFile.exists()) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response
                        .ok(backgroundFile, MediaType.valueOf(contentType))
                        .cacheControl(cacheControl)
                        .build();
            case LCU_IMAGE:
            case LCU_VIDEO:
                return Response
                        .status(Response.Status.TEMPORARY_REDIRECT)
                        .header("Location", "http://127.0.0.1:" + Starter.RESOURCE_SERVER_PORT + "/proxy/static" +backgroundModule.getBackground())
                        .build();
            default:
                return Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .build();
        }
    }

    @POST
    @Override
    public Response setConfig(JsonElement data) {
        if (data == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!data.isJsonObject()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        JsonObject jsonObject = data.getAsJsonObject();
        if (!Util.jsonKeysPresent(jsonObject, BackgroundModule.PROPERTY_BACKGROUND, BackgroundModule.PROPERTY_BACKGROUND_TYPE)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(BackgroundModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        BackgroundModule backgroundModule = (BackgroundModule) configModule;

        String background = jsonObject.get(BackgroundModule.PROPERTY_BACKGROUND).getAsString();
        String backgroundType = jsonObject.get(BackgroundModule.PROPERTY_BACKGROUND_TYPE).getAsString();

        backgroundModule.setBackground(background);
        backgroundModule.setBackgroundType(BackgroundModule.CLIENT_BACKGROUND_TYPE.fromString(backgroundType));

        return Response
                .status(Response.Status.CREATED)
                .build();
    }

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response getConfig() {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(BackgroundModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        BackgroundModule backgroundModule = (BackgroundModule) configModule;


        JsonObject info = new JsonObject();
        info.addProperty(BackgroundModule.PROPERTY_BACKGROUND, backgroundModule.getBackground());
        info.addProperty(BackgroundModule.PROPERTY_BACKGROUND_TYPE, backgroundModule.getBackgroundType().toString());
        info.addProperty(BackgroundModule.PROPERTY_BACKGROUND_CONTENT_TYPE, backgroundModule.getBackgroundContentType());

        return Response
                .status(Response.Status.OK)
                .entity(backgroundModule.getConfiguration().toString())
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadBackground(
            @FormDataParam("file") InputStream inputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail
    ) {
        ConfigModule configModule = Starter.getInstance().getConfigLoader().getConfigModule(BackgroundModule.class);
        if (configModule == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        BackgroundModule backgroundModule = (BackgroundModule) configModule;

        String fileName = fileDetail.getFileName();
        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

        Optional<String> contentType = getContentFromExtension(fileExtension, backgroundModule);
        if (!contentType.isPresent()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("File type not supported"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        String localFile = "background." + fileExtension;

        java.nio.file.Path backgroundPath = Starter.getInstance().getConfigLoader().getUserDataFolderPath().resolve((BackgroundModule.DIRECTORY));

        try {
            Files.copy(inputStream, backgroundPath.resolve(localFile), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Failed to save file"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        backgroundModule.setBackground(localFile);
        backgroundModule.setBackgroundContentType(contentType.get());

        return Response
                .status(Response.Status.OK)
                .build();
    }


    private Optional<String> getContentFromExtension(String fileExtension, BackgroundModule module) {
        switch (fileExtension) {
            case "png":
            case "jpeg":
            case "gif":
            case "jpg":
            case "webp":
                module.setBackgroundType(BackgroundModule.CLIENT_BACKGROUND_TYPE.LOCAL_IMAGE);
                return Optional.of("image/" + fileExtension);
            case "mp4":
            case "webm":
                module.setBackgroundType(BackgroundModule.CLIENT_BACKGROUND_TYPE.LOCAL_VIDEO);
                return Optional.of("video/" + fileExtension);
            default:
                Starter.getInstance().log("Unsupported file type: " + fileExtension, Starter.LOG_LEVEL.ERROR);
                return Optional.empty();
        }
    }
}
