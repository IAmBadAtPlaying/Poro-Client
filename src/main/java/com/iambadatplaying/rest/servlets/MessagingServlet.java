package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Path("/conversations")
public class MessagingServlet {
    @POST
    @Path("/{conversationId}")
    @Consumes("application/json")
    public Response sendMessage(@PathParam("conversationId") String conversationId, JsonElement messageJson) {
        try {
            conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ServletUtils.createErrorMessage("Invalid Conversation-Id")).build();
        }

        if (!checkValidConversationId(conversationId))
            return Response.status(Response.Status.BAD_REQUEST).entity(ServletUtils.createErrorMessage("Invalid Conversation-Id")).build();

        if (messageJson == null)
            return Response.status(Response.Status.BAD_REQUEST).entity(ServletUtils.createErrorMessage("Invalid JSON")).build();

        if (!messageJson.isJsonObject())
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON"))
                    .build();

        JsonObject jsonObject = messageJson.getAsJsonObject();

        if (!Util.jsonKeysPresent(jsonObject, "body"))
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Missing keys in JSON"))
                    .build();


        String body = jsonObject.get("body").getAsString();
        if (body.isEmpty()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Empty message"))
                    .build();
        }

        JsonObject lcuMessage = new JsonObject();
        lcuMessage.addProperty("body", body);
        lcuMessage.addProperty("type", "chat");


        HttpsURLConnection connection = Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-chat/v1/conversations/" + conversationId + "/messages", lcuMessage.toString());
        int responseCode = -1;
        try {
            responseCode = connection.getResponseCode();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ServletUtils.createErrorMessage("Failed to send message via LCU")).build();
        }

        switch (responseCode) {
            case 200:
            case 201:
                return Response.status(Response.Status.OK).build();
            default:
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ServletUtils.createErrorMessage("Failed to send message via LCU", "Returned status code: " + responseCode)).build();
        }
    }

    private boolean checkValidConversationId(String conversationId) {
        return !conversationId.isEmpty() && conversationId.contains("@");
    }
}
