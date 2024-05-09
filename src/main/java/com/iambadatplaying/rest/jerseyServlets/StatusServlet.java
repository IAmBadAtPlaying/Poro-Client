package com.iambadatplaying.rest.jerseyServlets;

import com.google.gson.JsonObject;
import com.iambadatplaying.ConnectionStatemachine;
import com.iambadatplaying.Starter;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/status")
public class StatusServlet {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        Starter currentInstance = Starter.getInstance();

        ConnectionStatemachine connectionStatemachine = Starter.getInstance().getConnectionStatemachine();
        boolean leagueAuth = currentInstance.getConnectionManager().isLeagueAuthDataAvailable();
        boolean shutdownPending = currentInstance.isShutdownPending();

        JsonObject status = new JsonObject();
        status.addProperty("state", connectionStatemachine.getCurrentState().toString());
        status.addProperty("authAvailable", leagueAuth);
        status.addProperty("shutdownPending", shutdownPending);

        return
                Response.status(Response.Status.OK)
                        .header("Content-Type", "application/json")
                        .entity(status)
                        .build();
    }

    @POST
    @Path("/findProcess")
    public Response findProcess() {
        ConnectionStatemachine connectionStatemachine = Starter.getInstance().getConnectionStatemachine();
        if (connectionStatemachine.getCurrentState() == ConnectionStatemachine.State.NO_PROCESS_IDLE) {
            connectionStatemachine.transition(ConnectionStatemachine.State.AWAITING_LEAGUE_PROCESS);
            return Response.status(Response.Status.OK).build();
        } else {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }
}
