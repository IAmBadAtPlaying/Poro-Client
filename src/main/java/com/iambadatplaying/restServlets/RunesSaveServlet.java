package com.iambadatplaying.restServlets;

import com.iambadatplaying.lcuHandler.ConnectionManager;
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

        deleteRunePage(pageId);
        createNewRunePage(json);


        resp.setStatus(HttpServletResponse.SC_OK);
    }


    private BigInteger getCurrentRunePageId() {
        JSONObject resp = (JSONObject) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.JSON_OBJECT,mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET,"/lol-perks/v1/currentpage"));
        if (resp == null) return null;
        BigInteger result = null;
        try {
            result = resp.getBigInteger("id");
        } catch (Exception e) {
            e.printStackTrace();
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
