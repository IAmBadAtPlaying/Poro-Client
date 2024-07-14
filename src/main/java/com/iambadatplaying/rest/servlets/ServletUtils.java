package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonObject;

import javax.ws.rs.core.Response;

public class ServletUtils {

    public static String createErrorMessage(String message) {
        return createErrorMessage(message, null);
    }

    public static String createErrorMessage(String message, String details) {
        return createErrorJson(message, details).toString();
    }

    public static JsonObject createErrorJson(String message, String details) {
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("error", message);

        if (details != null && !details.isEmpty()) {
            errorJson.addProperty("details", details);
        }

        return errorJson;
    }

    public static JsonObject createErrorJson(String message) {
        return createErrorJson(message, null);
    }
}
