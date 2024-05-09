package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.Starter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class BaseRESTServlet extends HttpServlet {
    protected Starter starter;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Content-Type", "application/json");
        if (req.getMethod().equals("OPTIONS")) {
            resp.setHeader("Cache-Control", "public, immutable, max-age=604800, must-understand");
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    protected JsonObject getJsonObjectFromRequestBody(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        String line;
        JsonObject json = new JsonObject();
        try {
            req.setCharacterEncoding(StandardCharsets.UTF_8.toString());
            while ((line = req.getReader().readLine() )!= null) {
                sb.append(line);
            }
            JsonElement element = new JsonParser().parse(sb.toString());
            if (element.isJsonObject()) {
                json = element.getAsJsonObject();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    protected JsonArray getJsonArrayFromRequestBody(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        String line;
        JsonArray json = new JsonArray();
        try {
            req.setCharacterEncoding(StandardCharsets.UTF_8.toString());
            while ((line = req.getReader().readLine() )!= null) {
                sb.append(line);
            }
            JsonElement element = new JsonParser().parse(sb.toString());
            if (element.isJsonArray()) {
                json = element.getAsJsonArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    protected String[] sliceAtSlash(String pathInfo) {
        if (pathInfo != null && pathInfo.length() > 1) {
            String path = pathInfo.substring(1); // remove leading slash
            String[] pathParts = path.split("/");
            return pathParts;
        }
        return new String[0];
    }


    protected JsonObject createResponse(String message) {
        return createResponse(message, null);
    }

    protected JsonObject createResponse(String message, JsonObject details) {
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("message", message);
        if (details != null) {
            responseJson.add("details", details);
        }
        return responseJson;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        starter = (Starter) getServletContext().getAttribute("mainInitiator");
    }

    public void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() +": " + s, level);
    }

    @Override
    public void log(String s) {
        starter.log(this.getClass().getSimpleName() +": " +s);
    }
}
