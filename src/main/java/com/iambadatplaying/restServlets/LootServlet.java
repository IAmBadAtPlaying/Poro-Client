package com.iambadatplaying.restServlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.MainInitiator;


import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

public class LootServlet extends BaseRESTServlet {

    public static final String ACTION_DISENCHANT = "disenchant";
    public static final String ACTION_REROll = "reroll";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathParts = sliceAtSlash(req.getPathInfo());
        if (pathParts.length == 0) {
            resp.setHeader("Content-Type", "application/json");

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("message","No path specified");
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
                responseJson.addProperty("message","Action type not supported");
                resp.getWriter().write(responseJson.toString());
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                break;
        }
    }

    private void handleReroll(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int ELEMENTS_REQUIRED_PER_REROLL = 3;

        JsonArray lootToDisenchant = getJsonArrayFromRequestBody(req);
        if (lootToDisenchant == null || lootToDisenchant.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        JsonArray notUsedElements = new JsonArray();
        // If the array % ELEMENTS_REQUIRED_PER_REROLL != 0, remove the last elements
        int elementsToRemove = lootToDisenchant.size() % ELEMENTS_REQUIRED_PER_REROLL;

        if (elementsToRemove > 0) {
            for (int i = 0; i < elementsToRemove; i++) {
                notUsedElements.add(lootToDisenchant.remove(lootToDisenchant.size() - 1));
            }
        }

        return;
    }

    private void handleDisenchant(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonArray lootToDisenchant = getJsonArrayFromRequestBody(req);
        if (lootToDisenchant == null || lootToDisenchant.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        int MAXIMUM_DISENCHANTS_PER_REQUEST = 50;

        ArrayList<JsonArray> disenchantCollections = new ArrayList<>();

        int actualCount = 0;
        for (int i = 0; i < lootToDisenchant.size(); i++) {
            JsonElement currentElement = lootToDisenchant.get(i);
            if (!currentElement.isJsonObject()) continue;
            final JsonObject currentLoot = currentElement.getAsJsonObject();
            if (!currentLoot.has("count")) continue;
            int count = currentLoot.get("count").getAsInt();
            if ((actualCount % MAXIMUM_DISENCHANTS_PER_REQUEST) + count >= MAXIMUM_DISENCHANTS_PER_REQUEST) {
                int firstPart = MAXIMUM_DISENCHANTS_PER_REQUEST - (actualCount % MAXIMUM_DISENCHANTS_PER_REQUEST);
                int secondPart = count - firstPart;

                disenchantCollections.get(actualCount / MAXIMUM_DISENCHANTS_PER_REQUEST).add(createBackendObject(currentLoot, firstPart));
                actualCount += firstPart;


                if (secondPart > 0) {
                    disenchantCollections.add(new JsonArray());
                    disenchantCollections.get(actualCount / MAXIMUM_DISENCHANTS_PER_REQUEST).add(createBackendObject(currentLoot, secondPart));
                    actualCount += secondPart;
                }
            } else {
                if (actualCount % MAXIMUM_DISENCHANTS_PER_REQUEST == 0) {
                    disenchantCollections.add(new JsonArray());
                }
                disenchantCollections.get(actualCount / MAXIMUM_DISENCHANTS_PER_REQUEST).add(createBackendObject(currentLoot, count));
                actualCount += count;
            }
        }

        for (JsonArray disenchantArray : disenchantCollections) {
            log(disenchantArray.toString(), MainInitiator.LOG_LEVEL.ERROR);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Content-Type", "application/json");

        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("disenchantCount", actualCount);
        responseJson.addProperty("message","Disenchanting successful");
        resp.getWriter().write(responseJson.toString());
    }

    private JsonObject createBackendObject(JsonObject frontendLootObject, int count) {
        if (frontendLootObject == null || frontendLootObject.isEmpty()) return null;
        if (count <= 0) return null;
        JsonObject disenchantLoot = new JsonObject();
        disenchantLoot.addProperty("repeat", count);
        disenchantLoot.addProperty("recipeName", frontendLootObject.get("type").getAsString() + "_disenchant");

        JsonArray lootNames = new JsonArray();
        lootNames.add(frontendLootObject.get("lootName").getAsString());
        disenchantLoot.add("lootNames", lootNames);

        return disenchantLoot;
    }
}
