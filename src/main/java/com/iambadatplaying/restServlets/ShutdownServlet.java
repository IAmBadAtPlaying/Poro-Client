package com.iambadatplaying.restServlets;

import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.ConnectionManager;

public class ShutdownServlet extends BaseRESTServlet{

    @Override
    public void doPost(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        JsonObject json = getJsonObjectFromRequestBody(request);
        if (json == null || json.isEmpty()) {
            handleNormalShutdown();
            return;
        }
        if (json.has("type")) {
            String shutdownType = json.get("type").getAsString();
            switch (shutdownType) {
                case "shutdown-all":
                    handleCombinedShutdown();
                    break;
                case "shutdown":
                default:
                    handleNormalShutdown();
                    break;
            }
        } else {
            handleNormalShutdown();
        }

        response.setStatus(javax.servlet.http.HttpServletResponse.SC_OK);
        response.setHeader("Content-Type", "application/json");
        response.setHeader("Access-Control-Allow-Origin", "*");

        JsonObject responseJson = new JsonObject();

        responseJson.addProperty("message", "Shutting down in one second, bye ^^");
        responseJson.addProperty("httpStatus", javax.servlet.http.HttpServletResponse.SC_OK);

        response.getWriter().println(responseJson.toString());
        response.getWriter().flush();

    }


    private void handleNormalShutdown() {
        new Thread(() -> {
            log("[Shutdown] Invoking Self-shutdown", Starter.LOG_LEVEL.INFO);
            String discBody = "{\"data\": {\"title\": \"Poro Client disconnected!\", \"details\": \"Have fun!\" }, \"critical\": false, \"detailKey\": \"pre_translated_details\",\"backgroundUrl\" : \"https://cdn.discordapp.com/attachments/313713209314115584/1067507653028364418/Test_2.01.png\",\"iconUrl\": \"/fe/lol-settings/poro_smile.png\", \"titleKey\": \"pre_translated_title\"}";
            starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/player-notifications/v1/notifications" , discBody));
            //Show Riot UX again so the user doesn't end up with league still running and them not noticing
            log("Sending Riot UX request", Starter.LOG_LEVEL.INFO);
            starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/riotclient/launch-ux", ""));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            starter.shutdown();
        }).start();
    }

    private void handleCombinedShutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/process-control/v1/process/quit", ""));
            log("[Shutdown] Invoking Self-shutdown", Starter.LOG_LEVEL.INFO);
            starter.shutdown();
        }).start();
    }
}
