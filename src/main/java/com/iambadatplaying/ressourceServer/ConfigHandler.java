package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.DataManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigHandler extends AbstractHandler {

    private final Starter starter;

    private final Path taskDir;

    public ConfigHandler(Starter starter) {
        super();
        this.starter = starter;
        this.taskDir = starter.getTaskPath();
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
        httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
        httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        httpServletResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Content-Disposition");

        if (s != null && s.startsWith("/upload")) {
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                handleUploadedFile(request);
                request.setHandled(true);
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            } else {
                httpServletResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
                request.setHandled(true);
            }

        }
    }

    private void handleUploadedFile(HttpServletRequest request) throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder requestBodyBuilder = new StringBuilder();

        try (InputStream inputStream = request.getInputStream()) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                requestBodyBuilder.append(new String(buffer, 0, bytesRead));
            }
        }

        String requestBody = requestBodyBuilder.toString().trim();

        String[] lines = requestBody.split("\\n");
        StringBuilder result = new StringBuilder();

        String contentTypeHeader = request.getHeader("Content-Type");
        String[] contentTypeHeaderParts = contentTypeHeader.split(";");
        String boundary = null;
        for (String contentTypeHeaderPart : contentTypeHeaderParts) {
            String part = contentTypeHeaderPart.trim();
            if (part.startsWith("boundary=")) {
                boundary = part.substring("boundary=".length());
            }
        }

        boolean fistEmptyLineFound = false;

        String fileName = null;

        for (String line : lines) {
            if (line == null) continue;
            if (line.trim().isEmpty()) {
                if (!fistEmptyLineFound) {
                    fistEmptyLineFound = true;
                }
            } else if (boundary != null && line.contains(boundary)) {
                continue;
            }
            if (fistEmptyLineFound) {
                result.append(line).append("\n");
            } else {
                if (line.startsWith("Content-Disposition:")) {
                    String[] parts = line.split(";");
                    for (String part : parts) {
                        String trimmedPart = part.trim();
                        if (trimmedPart.startsWith("filename=")) {
                            fileName = trimmedPart.substring("filename=".length());
                            fileName = fileName.substring(1, fileName.length() - 1);
                        }
                    }
                }
            }

        }
        log("File name: " + fileName);
        if (fileName == null) {
            log("File name is null, was deleted", Starter.LOG_LEVEL.INFO);
            return;
        } else if (!fileName.endsWith(".java")) {
            Files.deleteIfExists(taskDir.resolve(fileName));
            log("File is not a java file, was deleted", Starter.LOG_LEVEL.INFO);
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(taskDir.resolve(fileName).toFile()))) {
            writer.write(result.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        starter.getTaskManager().loadAtRuntime(fileName);
        String actualName = fileName.substring(0, fileName.length() - ".java".length());
        starter.getTaskManager().addTask(actualName);
        log(DataManager.getEventDataString("TaskUpdate", starter.getTaskManager().getTaskAndArgs()));
        starter.getServer().sendToAllSessions(DataManager.getEventDataString("TaskUpdate", starter.getTaskManager().getTaskAndArgs()));
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName()+ ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getSimpleName() + ": " + s);
    }

}

