package com.iambadatplaying.restServlets;

import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ChampSelectServlet extends BaseRESTServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String actionType = getActionTypeFromPath(req.getPathInfo());
        if (actionType == null || actionType.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        String line;
        JSONObject json = new JSONObject();
        try {
            while ((line = req.getReader().readLine() )!= null) {
                sb.append(line);
            }
            json = new JSONObject(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        switch (actionType) {
            case "pick":
                handlePick(resp, json);
                break;
            case "ban":
                handleBan(resp, json);
                break;
            default:
                break;
        }
    }

    private String getActionTypeFromPath(String pathInfo) {
        if (pathInfo != null && pathInfo.length() > 1) {
            String[] pathParts = pathInfo.split("/");
            String actionType = pathParts[pathParts.length - 1];
            if (actionType == null) return null;
            return actionType.toLowerCase();
        }
        return null;
    }

    private void handlePick(HttpServletResponse response, JSONObject json) {
        if (json == null || json.isEmpty()) return;
        try {
            Integer championId = json.getInt("championId");
            boolean isLockIn = json.getBoolean("lockIn");
            int responseCode = mainInitiator.getDataManager().pickChampion(championId, isLockIn);
            if (responseCode == -1) responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            response.setStatus(responseCode);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private void handleBan(HttpServletResponse response, JSONObject json) {
        if (json == null || json.isEmpty()) return;
        try {
            Integer championId = json.getInt("championId");
            boolean isLockIn = json.getBoolean("lockIn");
            int responseCode = mainInitiator.getDataManager().banChampion(championId, isLockIn);
            if (responseCode == -1) responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            response.setStatus(responseCode);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
