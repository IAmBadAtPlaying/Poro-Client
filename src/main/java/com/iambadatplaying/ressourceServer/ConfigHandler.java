package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.MainInitiator;
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

    private MainInitiator mainInitiator;

    private Path taskDir;

    public ConfigHandler(MainInitiator mainInitiator) {
        super();
        this.mainInitiator = mainInitiator;
        this.taskDir = mainInitiator.getTaskPath();
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
        httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
        httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        httpServletResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Content-Disposition");

        System.out.println("Got request: " + s);

        if (s != null) {
         if (s.startsWith("/upload")) {
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
                System.out.println("Boundary: " + boundary);
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
        if (!fileName.endsWith(".java")) {
            Files.deleteIfExists(taskDir.resolve(fileName));
            log("File is not a java file, was deleted", MainInitiator.LOG_LEVEL.INFO);
            return;
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(taskDir.resolve(fileName).toFile()));
        writer.write(result.toString());
        writer.close();

        mainInitiator.getTaskManager().loadAtRuntime(fileName);
        String actualName = fileName.substring(0, fileName.length() - ".java".length());
        mainInitiator.getTaskManager().addTask(actualName);
        log(DataManager.getEventDataString("TaskUpdate", mainInitiator.getTaskManager().getTaskAndArgs()));
        mainInitiator.getServer().sendToAllSessions(DataManager.getEventDataString("TaskUpdate", mainInitiator.getTaskManager().getTaskAndArgs()));
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}

