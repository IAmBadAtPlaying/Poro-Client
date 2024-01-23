package com.iambadatplaying.restServlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;

public class RunesSaveServlet extends BaseRESTServlet{
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonObject json = getJsonObjectFromRequestBody(req);
        if (json == null || json.isEmpty()) return;
        BigInteger pageId = getCurrentRunePageId();

        if (pageId == null) {
            pageId = getValidRunePageId();
        }

        deleteRunePage(pageId);
        createNewRunePage(json);


        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private BigInteger getValidRunePageId() {
        JsonArray resp = starter.getConnectionManager().getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-perks/v1/pages"));
        if (resp == null) return null;
        if (resp.isEmpty()) {
            log("No runepages found, creating new one", Starter.LOG_LEVEL.INFO);
            return null;
        }
        for (int i = resp.size()-1; i >= 0; i--) {
            JsonObject page = resp.get(i).getAsJsonObject();
            if (!page.get("isTemporary").getAsBoolean()) {
                log("Runepage \""+page.get("name").getAsString()+ "\" will be replaced!", Starter.LOG_LEVEL.INFO);
                return page.get("id").getAsBigInteger();
            }
        }
        return null;
    }

    private BigInteger getCurrentRunePageId() {
        JsonObject resp = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-perks/v1/currentpage"));
        if (resp == null) return null;
        BigInteger result = null;
        try {
            result = resp.get("id").getAsBigInteger();
        } catch (Exception e) {
            log("Current Rune page id not found, usually caused by using the rune presets", Starter.LOG_LEVEL.INFO);
        }
        return result;
    }

    private void deleteRunePage(BigInteger pageId) {
        if (pageId == null) return;
        starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.DELETE,"/lol-perks/v1/pages/"+pageId));
    }

    private void createNewRunePage(JsonObject body) {
        if (body == null || body.isEmpty()) return;
        starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST,"/lol-perks/v1/pages", body.toString()));
    }
}
