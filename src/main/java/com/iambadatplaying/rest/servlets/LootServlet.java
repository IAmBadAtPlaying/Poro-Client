package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.data.map.MapDataManager;
import com.iambadatplaying.data.state.LootDataManager;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/loot")
public class LootServlet {
    private static final int MAXIMUM_DISENCHANTS_PER_LCU_REQUEST = 50;
    private static final int ELEMENTS_REQUIRED_PER_REROLL        = 3;

    private static final String MATERIAL_KEY_ID = "MATERIAL_key";

    private static final int INT_DISENCHANT_MAXIMUM_POSSIBLE = -1;

    private static final String DISPLAY_CATEGORY_CHEST = "CHEST";

    private static final String RECIPE_NAME_REROLL = "REROLL";

    //Chest usually only have one operation (opening them)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/craft/{type}/{lootId}")
    public Response craftElement(
            @PathParam("type") String type,
            @PathParam("lootId") String lootId,
            JsonElement jsonElement
    ) {
        if (type == null || type.isEmpty()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid type", "The type is empty"))
                    .build();
        }

        if (RECIPE_NAME_REROLL.equalsIgnoreCase(type)) {
            return Response
                    .status(Response.Status.NOT_IMPLEMENTED)
                    .entity(ServletUtils.createErrorMessage("Not implemented", "Use the reroll endpoint to reroll loot instead"))
                    .build();
        }

        if (lootId == null || lootId.isEmpty()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid lootId", "The lootId is empty"))
                    .build();
        }


        if (jsonElement == null || !jsonElement.isJsonObject()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body is not a JSON Object"))
                    .build();
        }

        JsonObject craftData = jsonElement.getAsJsonObject();

        if (!Util.jsonKeysPresent(craftData, "count")) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body does not contain the required keys"))
                    .build();
        }

        int amountToCraft = craftData.get("count").getAsInt();
        type = type.toUpperCase();

        JsonArray possibleCrafts = ConnectionManager.getResponseBodyAsJsonArray(Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, "/lol-loot/v1/recipes/initial-item/" + lootId));
        if (possibleCrafts == null || possibleCrafts.isEmpty()) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(ServletUtils.createErrorMessage("No item found", "No recipes for item \"" + lootId + "\""))
                    .build();
        }

        log("Possible crafts: " + possibleCrafts, Starter.LOG_LEVEL.INFO);

        Map<String, JsonObject> possibleCraftsMap = MapDataManager.<String>getMapFromArray(possibleCrafts, "type", jElement -> jElement.getAsJsonPrimitive().getAsString());

        JsonObject possibleCraft = possibleCraftsMap.get(type);

        log("Possible craft: " + possibleCraft, Starter.LOG_LEVEL.INFO);

        if (possibleCraft == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(ServletUtils.createErrorMessage("No item found", "No recipes for item \"" + lootId + "\" with type \"" + type + "\""))
                    .build();
        }

        JsonArray slots = possibleCraft.getAsJsonArray("slots");

        int repeatAmount = slots.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(data -> getCraftableAmountForElement(data, amountToCraft))
                .mapToInt(i -> i)
                .min()
                .orElse(-1);

        if (repeatAmount == -1) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorMessage("Unable to craft", "Not enough resources to craft the item"))
                    .build();
        }


        String recipeName = possibleCraft.get("recipeName").getAsString();

        JsonArray request = possibleCraft.getAsJsonArray("slots")
                .asList()
                .stream()
                .map(JsonElement::getAsJsonObject)
                .map(data -> {
                    JsonArray elements = new JsonArray();
                    String lootIdForCraft = data.get("lootIds").getAsJsonArray().get(0).getAsString();
                    elements.add(lootIdForCraft);
                    return elements;
                }).reduce(new JsonArray(), (acc, elements) -> {
                    elements.forEach(acc::add);
                    return acc;
                });


        log("Crafting: " + request, Starter.LOG_LEVEL.INFO);

        JsonObject responseJson = ConnectionManager.getResponseBodyAsJsonObject(Starter.getInstance().getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/recipes/" + recipeName + "/craft?repeat=" + repeatAmount, request.toString()));
        if (responseJson == null || responseJson.has("errorCode")) {
            log("Failed to craft item", Starter.LOG_LEVEL.ERROR);
            log("Response: " + responseJson, Starter.LOG_LEVEL.ERROR);

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorMessage("Internal Server Error", "An error occurred while crafting the item"))
                    .build();
        }


        return Response
                .status(Response.Status.OK)
                .entity(responseJson)
                .build();
    }

    private Optional<String> getOptLootId(JsonObject craftData) {
        JsonArray lootIds = craftData.getAsJsonArray("lootIds");
        if (lootIds == null || lootIds.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(lootIds.get(0).getAsString());
        }
    }

    private int getCraftableAmountForElement(JsonObject craftData, int maximumCraftAmount) {
        int requiredQuantity = craftData.get("quantity").getAsInt();
        Optional<String> optLootId = getOptLootId(craftData);
        if (!optLootId.isPresent()) {
            return -1;
        }

        String lootId = optLootId.get();
        int ownedQuantity =
                Starter.getInstance()
                        .getDataManager()
                        .getStateManager(LootDataManager.class)
                        .getCurrentState()
                        .flatMap(state -> Optional.ofNullable(state.getAsJsonObject(lootId)))
                        .map(currentState -> Util.getOptInt(currentState, "count").orElse(-1))
                        .orElse(-1);

        if (ownedQuantity == -1) {
            return -1;
        }

        int maximumCraftableAmount = ownedQuantity / requiredQuantity;

        if (maximumCraftAmount == INT_DISENCHANT_MAXIMUM_POSSIBLE) {
            return maximumCraftableAmount;
        }

        return Math.min(maximumCraftAmount, maximumCraftableAmount);
    }

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

//    @POST
//    @Produces(MediaType.APPLICATION_JSON)
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Path("/chests/open/{chestId}")
//    public Response openChests(@PathParam("chestId") String chestId, JsonElement jsonElement) {
//        if (chestId == null || chestId.isEmpty()) {
//            return Response
//                    .status(Response.Status.BAD_REQUEST)
//                    .entity(ServletUtils.createErrorMessage("Invalid chestId", "The chestId is empty"))
//                    .build();
//        }
//
//        if (jsonElement == null || !jsonElement.isJsonObject()) {
//            return Response
//                    .status(Response.Status.BAD_REQUEST)
//                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body is not a JSON Object"))
//                    .build();
//        }
//
//        JsonObject chestData = jsonElement.getAsJsonObject();
//
//        if (!Util.jsonKeysPresent(chestData, "type")) {
//            return Response
//                    .status(Response.Status.BAD_REQUEST)
//                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body does not contain the required keys"))
//                    .build();
//        }
//
//        String requestType = chestData.get("type").getAsString();
//
//        Optional<CRAFT_MODE> craftMode = CRAFT_MODE.fromString(requestType);
//        if (!craftMode.isPresent()) {
//            return Response
//                    .status(Response.Status.BAD_REQUEST)
//                    .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body does not contain a valid type"))
//                    .build();
//        }
//
//        switch (craftMode.get()) {
//            case MAXIMUM_POSSIBLE:
//                return executeOpenChests(chestId, -1);
//            case COUNT:
//                if (!Util.jsonKeysPresent(chestData, "count")) {
//                    return Response
//                            .status(Response.Status.BAD_REQUEST)
//                            .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body does not contain the required keys"))
//                            .build();
//                }
//
//                int count = chestData.get("count").getAsInt();
//
//                if (count <= 0) {
//                    return Response
//                            .status(Response.Status.BAD_REQUEST)
//                            .entity(ServletUtils.createErrorMessage("Invalid JSON", "The count must be greater than 0"))
//                            .build();
//                }
//
//                return executeOpenChests(chestId, count);
//            default:
//                return Response
//                        .status(Response.Status.BAD_REQUEST)
//                        .entity(ServletUtils.createErrorMessage("Invalid JSON", "The message body does not contain a valid type"))
//                        .build();
//        }
//    }
//
//    private Response executeOpenChests(String chestId, int count) {
//        Starter starter = Starter.getInstance();
//        StateDataManager lootMapManager = starter.getDataManager().getStateManagers(LootDataManager.class);
//        if (lootMapManager == null) {
//            return Response
//                    .status(Response.Status.SERVICE_UNAVAILABLE)
//                    .entity(ServletUtils.createErrorMessage("DataManager not available", "The DataManager for LootData is not available"))
//                    .build();
//        }
//
//        LootDataManager lootData = (LootDataManager) lootMapManager;
//        Optional<JsonObject> optCurrentState = lootData.getCurrentState();
//        if (!optCurrentState.isPresent()) {
//            return Response
//                    .status(Response.Status.SERVICE_UNAVAILABLE)
//                    .entity(ServletUtils.createErrorMessage("DataManager not available", "The DataManager for LootData is not available"))
//                    .build();
//        }
//
//        JsonObject currentState = optCurrentState.get();
//        if (!Util.jsonKeysPresent(currentState, chestId)) {
//            return Response
//                    .status(Response.Status.SERVICE_UNAVAILABLE)
//                    .entity(ServletUtils.createErrorMessage("Chest not available", "The chest with the given id is not available"))
//                    .build();
//        }
//
//        int chestCount = currentState.get(chestId).getAsJsonObject().get("count").getAsInt();
//        if (chestCount <= 0) {
//            return Response
//                    .status(Response.Status.BAD_REQUEST)
//                    .entity(ServletUtils.createErrorMessage("Chest not available", "The chest with the given id is empty"))
//                    .build();
//        }
//
//        if (!Util.jsonKeysPresent(currentState, MATERIAL_KEY_ID)) {
//            return Response
//                    .status(Response.Status.SERVICE_UNAVAILABLE)
//                    .entity(ServletUtils.createErrorMessage("Key not available", "The key with the given id is not available"))
//                    .build();
//        }
//
//        int keyCount = currentState.get(MATERIAL_KEY_ID).getAsJsonObject().get("count").getAsInt();
//        if (keyCount <= 0) {
//            return Response
//                    .status(Response.Status.BAD_REQUEST)
//                    .entity(ServletUtils.createErrorMessage("Key not available", "No keys available to open the chest"))
//                    .build();
//        }
//
//        int possibleOpenCount = Math.min(chestCount, keyCount);
//        if (possibleOpenCount < count) {
//            return Response
//                    .status(Response.Status.BAD_REQUEST)
//                    .entity(ServletUtils.createErrorMessage("Not enough Resources", "You either need more keys or chests to fulfill the request"))
//                    .build();
//        }
//
//        int openCount = (count == -1) ? possibleOpenCount : count;
//
//        ConnectionManager conManager = starter.getConnectionManager();
//
//        JsonArray request = new JsonArray();
//        request.add(chestId);
//        request.add(MATERIAL_KEY_ID);
//
//        HttpsURLConnection connection = conManager.buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/recipes/" + chestId + "_OPEN/craft?repeat=" + openCount, request.toString());
//        JsonObject resp = ConnectionManager.getResponseBodyAsJsonObject(connection);
//        if (Util.jsonKeysPresent(resp, "errorCode")) {
//            return Response
//                    .status(Response.Status.INTERNAL_SERVER_ERROR)
//                    .entity(ServletUtils.createErrorMessage("Internal Server Error", "An error occurred while opening the chests"))
//                    .build();
//        }
//
//        JsonObject responseJson = new JsonObject();
//        responseJson.addProperty("message", "Opened " + openCount + " chests successfully");
//        responseJson.add("details", resp);
//
//        return Response
//                .status(Response.Status.OK)
//                .entity(responseJson)
//                .build();
//    }

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
