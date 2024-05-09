package com.iambadatplaying.rest.jerseyServlets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.ConnectionStatemachine;
import com.iambadatplaying.Starter;
import com.iambadatplaying.Util;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/shutdown")
public class ShutdownServlet {

    private static final String SHUTDOWN_ALL = "shutdown-all";
    private static final String SHUTDOWN_SIMPLE = "shutdown";

    private static final long SHUTDOWN_DELAY_MILISECONDS = 1000;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response shutdown(JsonElement config) {
        if (config == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!config.isJsonObject()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        JsonObject jsonObject = config.getAsJsonObject();

        if (Util.jsonKeysPresent(jsonObject,"type")) {
            String type = jsonObject.get("type").getAsString();
            switch (type) {
                case SHUTDOWN_ALL:
                    handleCombinedShutdown();
                    break;
                case SHUTDOWN_SIMPLE:
                default:
                break;
            }
        }

        handleNormalShutdown();
        return Response.ok().build();
    }


    private void handleCombinedShutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(SHUTDOWN_DELAY_MILISECONDS);
            } catch (InterruptedException e) {
                log("Error while waiting for shutdown", Starter.LOG_LEVEL.ERROR);
            }
            Starter starter = Starter.getInstance();
            log("[Shutdown] Invoking Self-shutdown (combined)", Starter.LOG_LEVEL.INFO);
            starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.RESPONSE_CODE, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/process-control/v1/process/quit", ""));
            starter.getConnectionStatemachine().transition(ConnectionStatemachine.State.STOPPING);
        }).start();
    }

    private void handleNormalShutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(SHUTDOWN_DELAY_MILISECONDS);
            } catch (InterruptedException e) {
                log("Error while waiting for shutdown", Starter.LOG_LEVEL.ERROR);
            }
            Starter starter = Starter.getInstance();
            log("[Shutdown] Invoking Self-shutdown", Starter.LOG_LEVEL.INFO);
            String discBody = "{\"data\": {\"title\": \"Poro Client disconnected!\", \"details\": \"Have fun!\" }, \"critical\": false, \"detailKey\": \"pre_translated_details\",\"backgroundUrl\" : \"https://cdn.discordapp.com/attachments/313713209314115584/1067507653028364418/Test_2.01.png\",\"iconUrl\": \"/fe/lol-settings/poro_smile.png\", \"titleKey\": \"pre_translated_title\"}";
            starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/player-notifications/v1/notifications" , discBody));
            //Show Riot UX again so the user doesn't end up with league still running and them not noticing
            log("Sending Riot UX request", Starter.LOG_LEVEL.INFO);
            starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.STRING, starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.POST, "/riotclient/launch-ux", ""));
            starter.getConnectionStatemachine().transition(ConnectionStatemachine.State.STOPPING);
        }).start();
    }

    private void log(String message) {
        log(message, Starter.LOG_LEVEL.DEBUG);
    }

    private void log(String message, Starter.LOG_LEVEL level) {
        Starter.getInstance().log(this.getClass().getSimpleName() +": "+ message, level);
    }
}
