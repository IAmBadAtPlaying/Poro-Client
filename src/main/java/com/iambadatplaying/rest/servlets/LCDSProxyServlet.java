package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.net.URLEncoder;
import java.util.Optional;

@Path("/lcds")
public class LCDSProxyServlet {

    @POST
    public Response proxyLCDSRequest(String body) {
        Optional<JsonElement> bodyJson = Util.parseJson(body);

        if (!bodyJson.isPresent()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON"))
                    .build();
        }

        JsonElement bodyElement = bodyJson.get();

        if (!bodyElement.isJsonObject()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON"))
                    .build();
        }

        JsonObject bodyObject = bodyElement.getAsJsonObject();

        if (!Util.jsonKeysPresent(bodyObject, "destination", "method", "args")) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON"))
                    .build();
        }

        JsonElement destination = bodyObject.get("destination");
        JsonElement method = bodyObject.get("method");
        JsonElement args = bodyObject.get("args");

        if (!destination.isJsonPrimitive()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Destination must be a string"))
                    .build();
        }

        if (!method.isJsonPrimitive()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Method must be a string"))
                    .build();
        }

        if (!args.isJsonArray()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Args must be an array"))
                    .build();
        }

        String destinationString = URLEncoder.encode(destination.getAsString());
        String methodString = URLEncoder.encode(method.getAsString());
        String argsString = URLEncoder.encode(args.toString());

        String resource = "/lol-login/v1/session/invoke?destination=" + destinationString + "&method=" + methodString + "&args=" + argsString;
        Starter starter = Starter.getInstance();
        JsonObject response = ConnectionManager.getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, resource));
        if (response == null) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorMessage("Internal server error"))
                    .build();
        }

        return Response
                .status(Response.Status.OK)
                .entity(response.toString())
                .build();
    }
}
