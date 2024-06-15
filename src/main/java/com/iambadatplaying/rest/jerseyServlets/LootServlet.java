package com.iambadatplaying.rest.jerseyServlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Path("/loot")
public class LootServlet {
    private static final int MAXIMUM_DISENCHANTS_PER_LCU_REQUEST = 50;
    private static final int ELEMENTS_REQUIRED_PER_REROLL = 3;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/disenchant")
    public Response disenchant(JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonArray()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body is not a JSON Array"))
                    .build();
        }

        JsonArray lootToDisenchant = jsonElement.getAsJsonArray();

        int currentArraySize = 0;
        ArrayList<JsonArray> disenchantCollections = new ArrayList<>();


        for (int i = 0; i < lootToDisenchant.size(); i++) {
            JsonElement currentElement = lootToDisenchant.get(i);
            if (!currentElement.isJsonObject()) {
                break;
            }

            JsonObject current = currentElement.getAsJsonObject();
            if (!Util.jsonKeysPresent(current, "type", "count", "lootId", "disenchantRecipeName")) {
                break;
            }

            String currentType = current.get("type").getAsString();
            Integer count = current.get("count").getAsInt();
            String lootId = current.get("lootId").getAsString();

            if (currentType.isEmpty() || count <= 0 || lootId.isEmpty()) {
                break;
            }

            JsonObject minimalObject = new JsonObject();
            Util.copyJsonAttributes(current, minimalObject, "type", "lootId", "count", "disenchantRecipeName");
            while (count > 0) {
                //Array was just filled up by the last iteration
                if (currentArraySize % MAXIMUM_DISENCHANTS_PER_LCU_REQUEST == 0) {
                    disenchantCollections.add(new JsonArray());
                    currentArraySize = 0;
                }


                while (currentArraySize + count > MAXIMUM_DISENCHANTS_PER_LCU_REQUEST) {
                    int remainingSpaceInCurrentArray = MAXIMUM_DISENCHANTS_PER_LCU_REQUEST - currentArraySize;

                    JsonObject disenchantObject = new JsonObject();
                    disenchantObject.addProperty("repeat", remainingSpaceInCurrentArray);
                    JsonArray lootNames = new JsonArray();
                    lootNames.add(lootId);
                    disenchantObject.add("lootNames", lootNames);
                    disenchantObject.addProperty("recipeName", current.get("disenchantRecipeName").getAsString());

                    disenchantCollections.get(disenchantCollections.size() - 1).add(disenchantObject);
                    count = count - remainingSpaceInCurrentArray;
                    currentArraySize = (currentArraySize + remainingSpaceInCurrentArray) % MAXIMUM_DISENCHANTS_PER_LCU_REQUEST;
                }

                JsonObject disenchantObject = new JsonObject();
                disenchantObject.addProperty("repeat", count);
                JsonArray lootNames = new JsonArray();
                lootNames.add(lootId);
                disenchantObject.add("lootNames", lootNames);
                disenchantObject.addProperty("recipeName", current.get("disenchantRecipeName").getAsString());

                disenchantCollections.get(disenchantCollections.size() - 1).add(disenchantObject);
                currentArraySize = (currentArraySize + count) % MAXIMUM_DISENCHANTS_PER_LCU_REQUEST;
                count = 0;
            }
        }

        if (disenchantCollections.isEmpty()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body contains invalid loot objects"))
                    .build();
        }

        boolean somethingFailed = false;
        ArrayList<JsonObject> responses = new ArrayList<>();
        loop:
        for (JsonArray requests : disenchantCollections) {
            HttpsURLConnection connection = Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/craft/mass", requests.toString());
            if (connection == null) {
                somethingFailed = true;
                break;
            }
            try {
                int responseCode = connection.getResponseCode();
                switch (responseCode) {
                    case 200:
                    case 204:
                        JsonObject responseJson = ConnectionManager.getResponseBodyAsJsonObject(connection);

                        if (responseJson.has("httpStatus") && responseJson.get("httpStatus").getAsInt() != 200) {
                            somethingFailed = true;
                            break loop;
                        }
                        responses.add(responseJson);
                        connection.disconnect();
                        break;
                    default:
                        log("Failed to disenchant loot, response code: " + responseCode, Starter.LOG_LEVEL.ERROR);
                        log("Response: " + ConnectionManager.handleStringResponse(connection), Starter.LOG_LEVEL.ERROR);
                        somethingFailed = true;
                        break loop;
                }
            } catch (Exception e) {
                continue;
            }
        }

        JsonObject responseJson = new JsonObject();
        JsonObject result = new JsonObject();

        for (String category : new String[]{"added", "redeemed", "removed"}) {
            JsonArray combined = responses.stream()
                    //Create Stream of JsonElements from all responses
                    .flatMap(response -> response.getAsJsonArray(category).getAsJsonArray().asList().stream())
                    //Group items into map by lootId
                    .collect(Collectors.groupingBy(item -> item.getAsJsonObject().get("playerLoot").getAsJsonObject().get("lootId").getAsString()))
                    .values().stream()
                    //Combine deltaCount of all items with the same lootId
                    .map(jsonElements -> {
                        JsonObject item = new JsonObject();
                        int totalDeltaCount = jsonElements.stream()
                                .mapToInt(i -> i.getAsJsonObject().get("deltaCount").getAsInt())
                                .sum();
                        item.addProperty("deltaCount", totalDeltaCount);
                        item.add("playerLoot", jsonElements.get(0).getAsJsonObject().get("playerLoot"));
                        return item;
                    })
                    //Filter out items with deltaCount of 0
                    .filter(item -> item.get("deltaCount").getAsInt() != 0)
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
            result.add(category, combined);
        }

        responseJson.add("details", result);

        if (somethingFailed) {
            responseJson.addProperty("message", "Not all loot could be disenchanted");

            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Internal Server Error", "An error occurred while disenchanting the loot"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        responseJson.addProperty("message", "Loot disenchanted successfully");

        return Response
                .ok(responseJson, MediaType.APPLICATION_JSON)
                .build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/reroll")
    public Response reroll(JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonArray()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body is not a JSON Array"))
                    .build();
        }

        JsonArray lootToReroll = jsonElement.getAsJsonArray();
        if (lootToReroll.isEmpty()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body is an empty JSON Array"))
                    .build();
        }

        String type = null;
        int currentArraySize = 0;
        ArrayList<JsonArray> rerollCollections = new ArrayList<>();
        for (int i = 0; i < lootToReroll.size(); i++) {
            JsonElement currentElement = lootToReroll.get(i);
            if (!currentElement.isJsonObject()) {
                break;
            }

            JsonObject current = currentElement.getAsJsonObject();
            if (!Util.jsonKeysPresent(current, "type", "lootId")) {
                break;
            }

            String currentType = current.get("type").getAsString();
            String lootId = current.get("lootId").getAsString();
            Integer count = current.get("count").getAsInt();

            if (currentType.isEmpty() || lootId.isEmpty() || count <= 0) {
                //Skip invalid loot objects
                continue;
            }

            if (type == null) {
                type = currentType;
            } else if (!type.equals(currentType)) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(ServletUtils.createErrorMessage("Invalid JSON", "All loot objects must have the same type"))
                        .build();
            }

            for (int j = 0; j < count; j++) {
                //Array was just filled up by the last iteration
                if (currentArraySize % ELEMENTS_REQUIRED_PER_REROLL == 0) {
                    rerollCollections.add(new JsonArray());
                    currentArraySize = 0;
                }

                rerollCollections.get(rerollCollections.size() - 1).add(lootId);
                currentArraySize++;
            }
        }

        if (rerollCollections.isEmpty()) {
            return Response
                    .status(Response.Status.NO_CONTENT)
                    .entity(ServletUtils.createErrorMessage("No Loot to disenchant", "The message body contains invalid loot objects"))
                    .build();
        }

        JsonArray notRerollableElements = new JsonArray();
        if (rerollCollections.get(rerollCollections.size() - 1).size() < ELEMENTS_REQUIRED_PER_REROLL) {
            notRerollableElements = rerollCollections.remove(rerollCollections.size() - 1);
        }

        boolean somethingFailed = false;
        ArrayList<JsonObject> responses = new ArrayList<>();
        disenchantLoop:
        for (JsonArray rerollArray : rerollCollections) {
            log("Rerolling: " + rerollArray.toString());
            log("Type: " + type);
            HttpsURLConnection connection = Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/recipes/" + removeRental(type) + "_reroll/craft?repeat=1", rerollArray.toString());
            if (connection == null) {
                somethingFailed = true;
                break;
            }
            try {
                int responseCode = connection.getResponseCode();
                switch (responseCode) {
                    case 200:
                    case 204:
                        JsonObject responseJson = ConnectionManager.getResponseBodyAsJsonObject(connection);

                        if (responseJson.has("httpStatus") && responseJson.get("httpStatus").getAsInt() != 200) {
                            somethingFailed = true;
                            break disenchantLoop;
                        }
                        responses.add(responseJson);
                        connection.disconnect();
                        break;
                    default:
                        log("Failed to disenchant loot, response code: " + responseCode, Starter.LOG_LEVEL.ERROR);
                        log("Response: " + ConnectionManager.handleStringResponse(connection), Starter.LOG_LEVEL.ERROR);
                        somethingFailed = true;
                        break disenchantLoop;
                }
            } catch (Exception e) {
                continue;
            }
        }

        JsonObject responseJson = new JsonObject();
        JsonObject result = new JsonObject();

        for (String category : new String[]{"added", "redeemed", "removed"}) {
            JsonArray combined = responses.stream()
                    //Create Stream of JsonElements from all responses
                    .flatMap(response -> response.getAsJsonArray(category).getAsJsonArray().asList().stream())
                    //Group items into map by lootId
                    .collect(Collectors.groupingBy(item -> item.getAsJsonObject().get("playerLoot").getAsJsonObject().get("lootId").getAsString()))
                    .values().stream()
                    //Combine deltaCount of all items with the same lootId
                    .map(jsonElements -> {
                        JsonObject item = new JsonObject();
                        int totalDeltaCount = jsonElements.stream()
                                .mapToInt(i -> i.getAsJsonObject().get("deltaCount").getAsInt())
                                .sum();
                        item.addProperty("deltaCount", totalDeltaCount);
                        item.add("playerLoot", jsonElements.get(0).getAsJsonObject().get("playerLoot"));
                        return item;
                    })
                    //Filter out items with deltaCount of 0
                    .filter(item -> item.get("deltaCount").getAsInt() != 0)
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
            result.add(category, combined);
        }

        responseJson.add("details", result);

        if (somethingFailed) {
            responseJson.addProperty("message", "Not all loot could be rerolled");

            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Internal Server Error", "An error occurred while disenchanting the loot"))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        responseJson.addProperty("message", "Loot rerolled successfully");
        responseJson.add("notRerolled", notRerollableElements);

        return Response
                .ok(responseJson, MediaType.APPLICATION_JSON)
                .build();
    }

    private String removeRental(String lootType) {
        if (lootType == null || lootType.isEmpty()) return "";
        if (lootType.contains("_RENTAL")) {
            return lootType.replaceFirst("_RENTAL", "");
        }
        return lootType;
    }

    private void log(String s) {
        log(s, Starter.LOG_LEVEL.DEBUG);
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        Starter.getInstance().log(s, level);
    }
}
