package com.iambadatplaying.restServlets;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;

public class RunesSaveServlet extends BaseRESTServlet{
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject json = getJsonFromRequestBody(req);
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
        JSONArray resp = (JSONArray) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_ARRAY,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-perks/v1/pages"));
        if (resp == null) return null;
        if (resp.isEmpty()) {
            log("No runepages found, creating new one", MainInitiator.LOG_LEVEL.INFO);
            return null;
        }
        for (int i = resp.length()-1; i >= 0; i--) {
            JSONObject page = resp.getJSONObject(i);
            if (!page.getBoolean("isTemporary")) {
                log("Runepage \""+page.getString("name")+ "\" will be replaced!", MainInitiator.LOG_LEVEL.INFO);
                return page.getBigInteger("id");
            }
        }
        return null;
    }

    private BigInteger getCurrentRunePageId() {
        JSONObject resp = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-perks/v1/currentpage"));
        if (resp == null) return null;
        BigInteger result = null;
        try {
            result = resp.getBigInteger("id");
        } catch (Exception e) {
            log("Current Rune page id not found, usually caused by using the rune presets", MainInitiator.LOG_LEVEL.INFO);
        }
        return result;
    }

    private void deleteRunePage(BigInteger pageId) {
        if (pageId == null) return;
        mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.DELETE,"/lol-perks/v1/pages/"+pageId));
    }

    private void createNewRunePage(JSONObject body) {
        if (body == null || body.isEmpty()) return;
        mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST,"/lol-perks/v1/pages", body.toString()));
    }
}
