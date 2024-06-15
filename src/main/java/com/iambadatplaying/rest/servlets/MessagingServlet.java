package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonObject;
import com.iambadatplaying.data.map.MessageManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class MessagingServlet extends BaseRESTServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathParts = sliceAtSlash(req.getPathInfo());
        if (pathParts.length == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String conversationId = pathParts[0];
        if (conversationId == null || conversationId.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (pathParts.length == 1) {
            // Get conversation
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Content-Type", "application/json");

            if (!conversationId.contains("pvp.net")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if (!conversationId.contains("@")) {
                conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            }

            Optional<JsonObject> optCurrentConversation = starter.getReworkedDataManager().getMapManagers(MessageManager.class).get(conversationId);
            if (!optCurrentConversation.isPresent()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            JsonObject jsonObject = optCurrentConversation.get();


            resp.getWriter().write(jsonObject.toString());
            resp.setStatus(HttpServletResponse.SC_OK);
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonObject requestJson = getJsonObjectFromRequestBody(req);
        if (requestJson == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String[] pathParts = sliceAtSlash(req.getPathInfo());
        if (pathParts.length == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String conversationId = pathParts[0];
        if (conversationId == null || conversationId.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (pathParts.length == 1) {
            // Send message
            if (!conversationId.contains("@")) {
                conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
            }
            if (!requestJson.has("body")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            String body = requestJson.get("body").getAsString();
            if (body.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            JsonObject messageJson = new JsonObject();
            messageJson.addProperty("body", body);
            messageJson.addProperty("type", "chat");

            JsonObject responseJson = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-chat/v1/conversations/" + conversationId + "/messages", messageJson.toString()));

            if (responseJson == null) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Content-Type", "application/json");
            resp.getWriter().write(responseJson.toString());

        }
    }
}
