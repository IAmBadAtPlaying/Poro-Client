package com.iambadatplaying.rest.jerseyServlets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.Arrays;

@Path("/userconfig")
public class UserConfigurationServlet {

    @GET
    public Response getUserConfig() {
        JsonObject config = Starter.getInstance().getConfigLoader().getConfig();

        if (config == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response
                .ok(config)
                .build();
    }

    @GET
    @Path("{path:.*}")
    public Response getUserConfig(@PathParam("path") String path) {
        String pathParts[] = path.split("/");

        JsonObject config = Starter.getInstance().getConfigLoader().getConfig();

        if (config == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        JsonElement configElement = config;

        for (String key: pathParts) {
            key = key == null ? "" : key.trim();

            if (configElement.isJsonObject()) {
                JsonObject configObject = configElement.getAsJsonObject();
                if (!configObject.has(key)) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                configElement = configObject.get(key);
            } else if (configElement.isJsonArray()) {
                try {
                    int index = Integer.parseInt(key);
                    if (index < 0 || index >= configElement.getAsJsonArray().size()) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                    configElement = configElement.getAsJsonArray().get(index);
                } catch (NumberFormatException e) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
            } else if (configElement.isJsonPrimitive()) {
                continue;
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }

        return Response
                .ok(configElement)
                .build();
    }
}
