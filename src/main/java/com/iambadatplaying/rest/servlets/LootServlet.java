package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

public class LootServlet extends BaseRESTServlet {

    public static final String ACTION_DISENCHANT = "disenchant";
    public static final String ACTION_REROll = "reroll";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathParts = sliceAtSlash(req.getPathInfo());
        if (pathParts.length == 0) {
            resp.setHeader("Content-Type", "application/json");

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("message", "No path specified");
            resp.getWriter().write(responseJson.toString());
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String actionType = pathParts[0];
        if (actionType == null || actionType.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        switch (actionType) {
            case ACTION_DISENCHANT:
                handleDisenchant(req, resp);
                break;
            case ACTION_REROll:
                handleReroll(req, resp);
                break;
            default:
                resp.setHeader("Content-Type", "application/json");
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("message", "Action type not supported");
                resp.getWriter().write(responseJson.toString());
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                break;
        }
    }

    private void handleReroll(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        final int ELEMENTS_REQUIRED_PER_REROLL = 3;

        JsonArray lootToDisenchant = getJsonArrayFromRequestBody(req);
        if (lootToDisenchant == null || lootToDisenchant.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            JsonObject details = new JsonObject();
            details.addProperty("reason", "No loot to disenchant");
            details.addProperty("recommendation", "Please provide loot to disenchant");
            resp.getWriter().write(createResponse("Disenchanting failed", details).toString());
            return;
        }

        String type = null;
        int currentArraySize = 0;
        ArrayList<JsonArray> rerollCollections = new ArrayList<>();
        for (int i = 0; i < lootToDisenchant.size(); i++) {
            JsonElement currentElement = lootToDisenchant.get(i);
            if (!currentElement.isJsonObject() || !Util.jsonKeysPresent(currentElement.getAsJsonObject(), "type", "lootId", "count")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                JsonObject details = new JsonObject();
                details.addProperty("reason", "At least one of the provided loot items is not a valid loot object");
                details.addProperty("recommendation", "Please provide valid loot objects");
                resp.getWriter().write(createResponse("Reroll failed", details).toString());
                return;
            }
            JsonObject current = currentElement.getAsJsonObject();
            String currentType = current.get("type").getAsString();

            if (type == null) {
                type = currentType;
            } else if (!type.equals(currentType)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                JsonObject details = new JsonObject();
                details.addProperty("reason", "At least one of the provided loot items is not of the same type as the others");
                details.addProperty("recommendation", "Please provide loot items of the same type");
                resp.getWriter().write(createResponse("Reroll failed", details).toString());
                return;
            }

            String lootId = current.get("lootId").getAsString();
            int count = current.get("count").getAsInt();

            if (lootId.isEmpty() || count <= 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                JsonObject details = new JsonObject();
                details.addProperty("reason", "At least one of the provided loot items has an invalid lootId or count");
                details.addProperty("recommendation", "Please provide valid loot items");
                resp.getWriter().write(createResponse("Reroll failed", details).toString());
                return;
            }

            for (int j = 0; j < count; j++) {
                if (currentArraySize % ELEMENTS_REQUIRED_PER_REROLL == 0) {
                    rerollCollections.add(new JsonArray());
                    currentArraySize = 0;
                }
                rerollCollections.get(rerollCollections.size() - 1).add(current.get("lootId").getAsString());
                currentArraySize++;
            }
        }

        if (rerollCollections.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            JsonObject details = new JsonObject();
            details.addProperty("reason", "No loot to reroll");
            details.addProperty("recommendation", "Please provide loot to reroll");
            resp.getWriter().write(createResponse("Reroll failed", details).toString());
            return;
        }

        JsonArray notRerolledElements = new JsonArray();
        if (rerollCollections.get(rerollCollections.size() - 1).size() < ELEMENTS_REQUIRED_PER_REROLL) {
            notRerolledElements = rerollCollections.remove(rerollCollections.size() - 1);
        }

        boolean somethingFailed = false;
        loop:
        for (JsonArray rerollArray : rerollCollections) {
            long time = System.currentTimeMillis();
            log("Rerolling: " + rerollArray.toString(), Starter.LOG_LEVEL.DEBUG);
            log(rerollArray.toString());
            HttpsURLConnection connection = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/recipes/" + removeRental(type) + "_reroll/craft?repeat=1", rerollArray.toString());
            if (connection == null) {
                somethingFailed = true;
                break;
            }

            int responseCode = connection.getResponseCode();
            switch (responseCode) {
                case 200:
                case 204:
                    JsonObject responseJson = ConnectionManager.getResponseBodyAsJsonObject(connection);

                    if (responseJson.has("httpStatus") && responseJson.get("httpStatus").getAsInt() != 200) {
                        somethingFailed = true;
                        break loop;
                    }
                    log("Rerolling response: " + responseJson, Starter.LOG_LEVEL.DEBUG);
                    connection.disconnect();
                    long time2 = System.currentTimeMillis();
                    log("Rerolling took: " + (time2 - time) + "ms", Starter.LOG_LEVEL.DEBUG);
                    break;
                default:
                    log(ConnectionManager.getResponseBodyAsJsonObject(connection).toString(), Starter.LOG_LEVEL.ERROR);
                    somethingFailed = true;
                    connection.disconnect();
                    break loop;
            }
        }

        if (somethingFailed) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            JsonObject details = new JsonObject();
            details.addProperty("reason", "At least one of " + rerollCollections.size() + " rerolls failed");
            details.addProperty("recommendation", "Update Frontend, maybe some of the loot was already used");
            resp.getWriter().write(createResponse("Rerolling failed", details).toString());
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);

        JsonObject details = new JsonObject();
        details.addProperty("rerollCount", rerollCollections.size());
        details.add("notRerolledElements", notRerolledElements);
        resp.getWriter().write(createResponse("Rerolling successful", details).toString());
    }

    private String removeRental(String lootType) {
        if (lootType == null || lootType.isEmpty()) return "";
        if (lootType.contains("_RENTAL")) {
            return lootType.replaceFirst("_RENTAL", "");
        }
        return lootType;
    }


    private void handleDisenchant(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        final int MAXIMUM_DISENCHANTS_PER_REQUEST = 50;

        JsonArray lootToDisenchant = getJsonArrayFromRequestBody(req);
        if (lootToDisenchant == null || lootToDisenchant.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        int currentArraySize = 0;
        ArrayList<JsonArray> disenchantCollections = new ArrayList<>();

        for (int i = 0; i < lootToDisenchant.size(); i++) {
            JsonElement currentElement = lootToDisenchant.get(i);
            if (!currentElement.isJsonObject()) {
                JsonObject details = new JsonObject();
                details.addProperty("reason", "The provided Element " + currentElement + " is not a valid loot object");
                details.addProperty("recommendation", "Please provide valid loot objects");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(createResponse("Disenchanting failed", details).toString());
                return;
            }
            JsonObject current = currentElement.getAsJsonObject();
            if (!Util.jsonKeysPresent(current, "type", "count", "lootId", "disenchantRecipeName")) {
                JsonObject details = new JsonObject();
                details.addProperty("reason", "The provided Element " + current + " is not a valid loot object, missing type, count or lootId");
                details.addProperty("recommendation", "Please provide loot objects with type, count and lootId");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(createResponse("Disenchanting failed", details).toString());
                return;
            }

            String currentType = current.get("type").getAsString();
            Integer count = current.get("count").getAsInt();
            String lootId = current.get("lootId").getAsString();

            if (currentType.isEmpty() || count <= 0 || lootId.isEmpty()) {
                JsonObject details = new JsonObject();
                details.addProperty("reason", "The provided Element " + current + " is not a valid loot object, type, count or lootId are invalid");
                details.addProperty("recommendation", "Please check the values of type, count and lootId");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(createResponse("Disenchanting failed", details).toString());
                return;
            }

            JsonObject minimalObject = new JsonObject();
            Util.copyJsonAttributes(current, minimalObject, "type", "lootId", "count", "disenchantRecipeName");
            while (count > 0) {
                if (currentArraySize % MAXIMUM_DISENCHANTS_PER_REQUEST == 0) {
                    disenchantCollections.add(new JsonArray());
                    currentArraySize = 0;
                }

                if (currentArraySize + count > MAXIMUM_DISENCHANTS_PER_REQUEST) {
                    int remainingSpaceInCurrentArray = MAXIMUM_DISENCHANTS_PER_REQUEST - currentArraySize;

                    JsonObject disenchantObject = new JsonObject();
                    disenchantObject.addProperty("repeat", remainingSpaceInCurrentArray);
                    JsonArray lootNames = new JsonArray();
                    lootNames.add(lootId);
                    disenchantObject.add("lootNames", lootNames);
                    disenchantObject.addProperty("recipeName", current.get("disenchantRecipeName").getAsString());

                    disenchantCollections.get(disenchantCollections.size() - 1).add(disenchantObject);
                    count = count - remainingSpaceInCurrentArray;
                    currentArraySize = (currentArraySize + remainingSpaceInCurrentArray) % MAXIMUM_DISENCHANTS_PER_REQUEST;
                } else {
                    JsonObject disenchantObject = new JsonObject();
                    disenchantObject.addProperty("repeat", count);
                    JsonArray lootNames = new JsonArray();
                    lootNames.add(lootId);
                    disenchantObject.add("lootNames", lootNames);
                    disenchantObject.addProperty("recipeName", current.get("disenchantRecipeName").getAsString());

                    disenchantCollections.get(disenchantCollections.size() - 1).add(disenchantObject);
                    currentArraySize = (currentArraySize + count) % MAXIMUM_DISENCHANTS_PER_REQUEST;
                    count = 0;
                }
            }
        }

        boolean somethingFailed = false;
        loop:
        for (JsonArray requests : disenchantCollections) {
            log("Disenchanting: " + requests.toString());
            HttpsURLConnection connection = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/lol-loot/v1/craft/mass", requests.toString());
            if (connection == null) {
                somethingFailed = true;
                break;
            }

            int responseCode = connection.getResponseCode();
            switch (responseCode) {
                case 200:
                case 204:
                    JsonObject responseJson = ConnectionManager.getResponseBodyAsJsonObject(connection);

                    log("Disenchanting response: " + responseJson.toString(), Starter.LOG_LEVEL.DEBUG);
                    if (responseJson.has("httpStatus") && responseJson.get("httpStatus").getAsInt() != 200) {
                        somethingFailed = true;
                        break loop;
                    }
                    connection.disconnect();
                    break;
                default:
                    somethingFailed = true;
                    break loop;
            }
        }

        if (somethingFailed) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            JsonObject details = new JsonObject();
            details.addProperty("reason", "At least one of " + disenchantCollections.size() + " disenchant requests failed");
            details.addProperty("recommendation", "Update Frontend, maybe some of the loot was already used");
            resp.getWriter().write(createResponse("Disenchanting failed", details).toString());
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        JsonObject details = new JsonObject();
        details.addProperty("disenchantCount", disenchantCollections.size());
        resp.getWriter().write(createResponse("Disenchanting successful", details).toString());
    }


    private Optional<JsonObject> createBackendObject(JsonObject frontendLootObject, int count) {
        if (frontendLootObject == null || frontendLootObject.isEmpty()) return Optional.empty();
        if (count <= 0) return Optional.empty();
        if (!Util.jsonKeysPresent(frontendLootObject, "type", "lootName")) return Optional.empty();
        JsonObject disenchantLoot = new JsonObject();
        disenchantLoot.addProperty("repeat", count);
        disenchantLoot.addProperty("recipeName", removeRental(frontendLootObject.get("type").getAsString()) + "_disenchant");

        JsonArray lootNames = new JsonArray();
        lootNames.add(frontendLootObject.get("lootName").getAsString());
        disenchantLoot.add("lootNames", lootNames);

        return Optional.of(disenchantLoot);
    }
}
