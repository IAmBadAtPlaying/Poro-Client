package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

public class RunesSaveServlet extends BaseRESTServlet{
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonObject json = getJsonObjectFromRequestBody(req);
        if (json == null || json.isEmpty()) return;
        Optional<BigInteger> pageId = getCurrentRunePageId();

        if (!pageId.isPresent()) {
            pageId = getValidRunePageId();
        }

        pageId.ifPresent(this::deleteRunePage);
        createNewRunePage(json);


        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private Optional<BigInteger> getValidRunePageId() {
        JsonArray resp = starter.getConnectionManager().getResponseBodyAsJsonArray(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-perks/v1/pages"));
        if (resp == null) return Optional.empty();
        if (resp.isEmpty()) {
            log("No runepages found, creating new one", Starter.LOG_LEVEL.INFO);
            return Optional.empty();
        }
        JsonObject lastPage = null;
        for (int i = 0; i < resp.size(); i++) {
            JsonObject page = resp.get(i).getAsJsonObject();
            if (!page.get("isTemporary").getAsBoolean()) {
                lastPage = page;
                if (page.get("name").getAsString().startsWith("Poro-Client")) {
                    log("Runepage \""+page.get("name").getAsString()+ "\" will be replaced!", Starter.LOG_LEVEL.INFO);
                    return Optional.of(page.get("id").getAsBigInteger());
                }
            }
        }
        if (lastPage != null) {
            log("Runepage \""+lastPage.get("name").getAsString()+ "\" will be replaced!", Starter.LOG_LEVEL.INFO);
            return Optional.of(lastPage.get("id").getAsBigInteger());
        }
        return Optional.empty();
    }

    private Optional<BigInteger> getCurrentRunePageId() {
        JsonObject resp = starter.getConnectionManager().getResponseBodyAsJsonObject(starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-perks/v1/currentpage"));
        if (resp == null) return Optional.empty();
        Optional<BigInteger> result = Optional.empty();
        if (!Util.jsonKeysPresent(resp, "id", "")) return Optional.empty();
        result = Optional.of(resp.get("id").getAsBigInteger());
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
