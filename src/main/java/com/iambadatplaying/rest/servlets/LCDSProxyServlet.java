package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

public class LCDSProxyServlet extends BaseRESTServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonObject json = getJsonObjectFromRequestBody(req);
        if (json == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!Util.jsonKeysPresent(json, "destination", "method", "args")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(createResponse("Missing required fields").toString());
            return;
        }

        JsonElement destination = json.get("destination");
        JsonElement method = json.get("method");
        JsonElement args = json.get("args");

        if (!destination.isJsonPrimitive()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(createResponse("Destination must be a string").toString());
            return;
        }

        if (!method.isJsonPrimitive()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(createResponse("Method must be a string").toString());
            return;
        }

        if (!args.isJsonArray()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(createResponse("Args must be an array").toString());
            return;
        }

        String destinationString = URLEncoder.encode(destination.getAsString(), "UTF-8");
        String methodString = URLEncoder.encode(method.getAsString(), "UTF-8");
        String argsString = URLEncoder.encode(args.toString(), "UTF-8");


        String ressource = "/lol-login/v1/session/invoke?destination=" + destinationString + "&method=" + methodString + "&args=" + argsString;
        JsonObject response = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, ressource));
        if (response == null) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(createResponse("Internal server error").toString());
            return;
        }

        resp.getWriter().write(response.toString());
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
