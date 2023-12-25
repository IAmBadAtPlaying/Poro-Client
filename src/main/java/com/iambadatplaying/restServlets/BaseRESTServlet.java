package com.iambadatplaying.restServlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.MainInitiator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class BaseRESTServlet extends HttpServlet {
    protected MainInitiator mainInitiator;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
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

    @Override
    public void init() throws ServletException {
        super.init();
        mainInitiator = (MainInitiator) getServletContext().getAttribute("mainInitiator");
    }

    public void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + s, level);
    }

    @Override
    public void log(String s) {
        mainInitiator.log(this.getClass().getSimpleName() +": " +s);
    }
}
