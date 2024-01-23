package com.iambadatplaying.restServlets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.iambadatplaying.ConfigLoader;
import com.iambadatplaying.Starter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class UploadServlet extends BaseRESTServlet {

    public static final String PATH_USERDATA = "userdata";

    private static final String PATH_USERDATA_CUSTOM_BACKGROUND = "background";

    private static final Gson gson = new GsonBuilder().create();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathParts = sliceAtSlash(req.getPathInfo());

        if (pathParts.length == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        switch (pathParts[0]) {
            case PATH_USERDATA:
                handleGetUserData(pathParts, req, resp);
            break;
            default:
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
        }
    }

    private void handleGetUserData(String[] pathParts, HttpServletRequest req, HttpServletResponse resp) {
        if (pathParts.length < 2) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        switch (pathParts[1]) {
            case PATH_USERDATA_CUSTOM_BACKGROUND:
                handleGetBackground(req, resp);
            break;
            default:
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
        }
    }

    private void handleGetBackground(HttpServletRequest req, HttpServletResponse resp) {
        Path path = starter.getConfigLoader().getAppFolderPath().resolve(ConfigLoader.USER_DATA_FOLDER_NAME);

        JsonObject clientProperties = starter.getConfigLoader().getClientProperties();

        String filename = clientProperties.get(
                ConfigLoader.PROPERTY_CLIENT_BACKGROUND
        ).getAsString();

        path = path.resolve(filename);

        String contentType = clientProperties.get(
                ConfigLoader.PROPERTY_CLIENT_BACKGROUND_CONTENT_TYPE
        ).getAsString();

        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setContentType(contentType);
        serveFile(req, resp, path);
    }

    private void serveFile(HttpServletRequest req, HttpServletResponse resp, Path path) {
        File file = path.toFile();
        if (!file.exists()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            resp.getOutputStream().write(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathParts = sliceAtSlash(req.getPathInfo());

        if (pathParts.length == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        switch (pathParts[0]) {
            case PATH_USERDATA:
                handleUploadUserData(pathParts, req, resp);
            break;
            default:
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
        }
    }

    private void handleUploadUserData(String[] pathParts,HttpServletRequest req, HttpServletResponse resp) {
        if (pathParts.length < 2) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        switch (pathParts[1]) {
            case PATH_USERDATA_CUSTOM_BACKGROUND:
                handleUploadBackground(req, resp);
            break;
            default:
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
        }
    }


    private void handleUploadBackground(HttpServletRequest req, HttpServletResponse resp) {
        Path path = starter.getConfigLoader().getAppFolderPath().resolve(ConfigLoader.USER_DATA_FOLDER_NAME).resolve("background");

        try {
            handleFileUpload(req, resp, path);
        } catch (IOException e) {
            starter.log("Failed to handle file upload: " + e.getMessage());
        }

        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private void handleUploadBackgroundVideo(HttpServletRequest req, HttpServletResponse resp) {
        Path path = starter.getConfigLoader().getAppFolderPath().resolve(ConfigLoader.USER_DATA_FOLDER_NAME).resolve("backgroundVideo");

        JsonObject clientProperties = starter.getConfigLoader().getClientProperties();

        clientProperties.addProperty(
                ConfigLoader.PROPERTY_CLIENT_BACKGROUND_TYPE,
                ConfigLoader.CLIENT_BACKGROUND_TYPE.LOCAL_VIDEO.toString()
        );

        try {
            handleFileUpload(req, resp, path);
        } catch (IOException e) {
            starter.log("Failed to handle file upload: " + e.getMessage());
        }

        resp.setStatus(HttpServletResponse.SC_OK);
    }


    private void handleFileUpload(HttpServletRequest req, HttpServletResponse resp, Path path) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;

            String contentTypeHeader = req.getHeader("Content-Type");
            String[] contentTypeHeaderParts = contentTypeHeader.split(";");
            String boundary = null;
            for (String contentTypeHeaderPart : contentTypeHeaderParts) {
                String part = contentTypeHeaderPart.trim();
                if (part.startsWith("boundary=")) {
                    boundary = part.substring("boundary=".length());
                }
            }

            if (boundary == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Read the input stream until the end
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputBuffer.write(buffer, 0, bytesRead);
            }

            // Convert the buffer to a byte array
            byte[] requestBody = outputBuffer.toByteArray();

            // Find the start and end indices of the file content within the request body
            int startIndex = findStartOfContent(requestBody, boundary);
            int endIndex = findEndOfContent(requestBody, boundary);

            if (startIndex == -1 || endIndex == -1) {
                log(""+startIndex);
                log(""+endIndex);
                log("Failed to find start or end of file content", Starter.LOG_LEVEL.ERROR);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            byte[] header = Arrays.copyOfRange(requestBody, 0, startIndex);
            String headerString = new String(header);
            String[] headerLines = headerString.split("\r\n");
            ConfigLoader configLoader = starter.getConfigLoader();
            String fileName = null;
            String extension = null;
            String contentType = null;
            for (String headerLine : headerLines) {
                if (headerLine.startsWith("Content-Disposition:")) {
                    String[] parts = headerLine.split(";");
                    for (String part : parts) {
                        String trimmedPart = part.trim();
                        if (trimmedPart.startsWith("filename=")) {
                            fileName = trimmedPart.substring("filename=".length()).trim();
                        }
                    }
                } else if (headerLine.startsWith("Content-Type:")) {
                    String[] parts = headerLine.split(":");
                    if (parts.length > 1) {
                        String cType = parts[1].trim();
                        switch (cType) {
                            case "image/jpeg":
                            case "image/png":
                            case "image/gif":
                            case "image/webp":
                                contentType = cType;
                                extension = cType.split("/")[1];

                                configLoader.setClientProperty(
                                        ConfigLoader.PROPERTY_CLIENT_BACKGROUND_TYPE,
                                        ConfigLoader.CLIENT_BACKGROUND_TYPE.LOCAL_IMAGE.toString()
                                );
                                break;
                            case "video/mp4":
                            case "video/webm":
                            case "video/ogg":
                                contentType = cType;
                                extension = cType.split("/")[1];

                                configLoader.setClientProperty(
                                        ConfigLoader.PROPERTY_CLIENT_BACKGROUND_TYPE,
                                        ConfigLoader.CLIENT_BACKGROUND_TYPE.LOCAL_VIDEO.toString()
                                );
                                break;
                            default:
                                log("Invalid content type: " + cType, Starter.LOG_LEVEL.ERROR);
                                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                return;
                        }
                    }
                }
            }

            if (fileName == null || extension == null || contentType == null) {
                log("Failed to find file name, extension or content type", Starter.LOG_LEVEL.ERROR);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }



            String relativePath = (path.toString()).replace(configLoader.getAppFolderPath().toString()+File.separator+ConfigLoader.USER_DATA_FOLDER_NAME+File.separator, "")+ "." + extension;

            configLoader.setClientProperty(ConfigLoader.PROPERTY_CLIENT_BACKGROUND, relativePath);
            configLoader.setClientProperty(ConfigLoader.PROPERTY_CLIENT_BACKGROUND_CONTENT_TYPE, contentType);


            // Extract the file content
            byte[] fileContent = Arrays.copyOfRange(requestBody, startIndex, endIndex);

            // Save the file content to the specified path
            saveFile(fileContent, path, extension);
        }
    }

    private int findStartOfContent(byte[] requestBody, String boundary) {
        String startMarker =  "\r\n\r\n";
        return indexOf(requestBody, startMarker.getBytes())+startMarker.length();
    }

    private int findEndOfContent(byte[] requestBody, String boundary) {
        String endMarker = boundary+"--\r\n";
        return indexOf(requestBody, endMarker.getBytes());
    }

    private int indexOf(byte[] array, byte[] target) {
        for (int i = 0; i <= array.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private void saveFile(byte[] fileBytes, Path path, String extension) throws IOException {
        path = Paths.get(path.toString() + "." + extension);
        //Check if the file already exists
        if (Files.exists(path)) {
            Files.delete(path);
        }
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            fos.write(fileBytes);
        }
    }
}
