package com.iambadatplaying.restServlets;

import com.google.gson.JsonObject;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import com.iambadatplaying.structs.messaging.Conversation;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class MessagingServlet extends BaseRESTServlet{
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

            Conversation conversation = mainInitiator.getDataManager().getConversation(conversationId);
            if (conversation == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if (conversation.getMessages().isEmpty()) {
                resp.setHeader("Refresh", "1");
            }
            JsonObject jsonObject = conversation.toJsonObject();
            resp.getWriter().write(jsonObject.toString());
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonObject requestJson = getJsonObjectFromRequestBody(req);
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
            String body = requestJson.get("body").getAsString();
            if (body == null || body.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            JsonObject messageJson = new JsonObject();
            messageJson.addProperty("body", body);
            messageJson.addProperty("type", "chat");

            JsonObject responseJson = mainInitiator.getConnectionManager().getResponseBodyAsJsonObject(mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-chat/v1/conversations/" + conversationId + "/messages", messageJson.toString()));

            if (responseJson == null) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Content-Type", "application/json");
            resp.getWriter().write(responseJson.toString());

            return;
        }
    }
}
