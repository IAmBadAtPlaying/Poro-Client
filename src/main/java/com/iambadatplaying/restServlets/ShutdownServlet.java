package com.iambadatplaying.restServlets;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.json.JSONObject;

import java.util.TimerTask;

import static com.iambadatplaying.MainInitiator.log;

public class ShutdownServlet extends BaseRESTServlet{

    @Override
    public void doPost(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        JSONObject json = getJsonFromRequestBody(request);
        if (json == null || json.isEmpty()) handleNormalShutdown();
        System.out.println(json.toString());
        if (json.has("type")) {
            String shutdownType = json.getString("type");
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

        JSONObject responseJson = new JSONObject();

        responseJson.put("message", "Shutting down in one second, bye ^^");
        responseJson.put("httpStatus", javax.servlet.http.HttpServletResponse.SC_OK);



        response.getWriter().println(responseJson.toString());
        response.getWriter().flush();

    }


    private void handleNormalShutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log("[Shutdown] Invoking Self-shutdown", MainInitiator.LOG_LEVEL.INFO);
            mainInitiator.shutdown();
        }).start();
    }

    private void handleCombinedShutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String discBody = "{\"data\": {\"title\": \"Poro Client disconnected!\", \"details\": \"Poro-Client shutdown successful\" }, \"critical\": false, \"detailKey\": \"pre_translated_details\",\"backgroundUrl\" : \"https://cdn.discordapp.com/attachments/313713209314115584/1067507653028364418/Test_2.01.png\",\"iconUrl\": \"https://raw.communitydragon.org/latest/plugins/rcp-fe-lol-settings/global/default/poro_smile.png\", \"titleKey\": \"pre_translated_title\"}";
            mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/player-notifications/v1/notifications" , discBody));
            //Show Riot UX again so the user doesn't end up with league still running and them not noticing
            log("Sending Riot UX request", MainInitiator.LOG_LEVEL.INFO);
            mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/riotclient/launch-ux", ""));
            log("[Shutdown] Invoking Self-shutdown", MainInitiator.LOG_LEVEL.INFO);
            mainInitiator.shutdown();
        }).start();
    }
}
