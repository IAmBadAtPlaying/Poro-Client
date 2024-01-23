package com.iambadatplaying.restServlets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class Userconfig extends BaseRESTServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathParts = sliceAtSlash(req.getPathInfo());

        JsonObject config = starter.getConfigLoader().getConfig();
        if (config == null) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        if (pathParts.length == 0) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Content-Type", "application/json");
            resp.getWriter().write(config.toString());
            return;
        }

        JsonObject configPart = config;
        for (String part : pathParts) {
            if (configPart.has(part)) {
                JsonElement o = configPart.get(part);
                if (o.isJsonObject()) {
                    configPart = o.getAsJsonObject();
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Content-Type", "application/json");
        resp.getWriter().write(configPart.toString());
    }

//    @Override
//    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//        String[] pathParts = sliceAtSlash(req.getPathInfo());
//
//        JSONObject config = mainInitiator.getConfigLoader().getConfig();
//        if (config == null) {
//            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//            return;
//        }
//
//        if (pathParts.length == 0) {
//            resp.setStatus(HttpServletResponse.SC_OK);
//            resp.setHeader("Content-Type", "application/json");
//            resp.getWriter().write(config.toString());
//            return;
//        }
//
//        JSONObject configToWrite = getJsonFromRequestBody(req);
//
//        JSONObject configPart = config;
//        recursiveGetWriteRessource(configPart, pathParts, 0)
//
//        resp.setStatus(HttpServletResponse.SC_OK);
//    }
//
//    private JSONObject recursiveGetWriteRessource(JSONObject config, String[] pathParts, Object configToWrite, int index) {
//        if (index == pathParts.length - 1) {
//            config.put(pathParts[index], configToWrite);
//            return config;
//        }
//        Object o = config.get(pathParts[index]);
//        if (o instanceof JSONObject) {
//            configPart = (JSONObject) o;
//        } else {
//            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
//            return;
//        }
//    }
}

