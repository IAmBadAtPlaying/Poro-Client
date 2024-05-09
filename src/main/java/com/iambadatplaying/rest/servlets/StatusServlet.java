package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;


public class StatusServlet extends BaseRESTServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = response.getWriter()) {
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("shutting_down", starter.isShutdownPending());
            responseJson.addProperty("state", starter.getConnectionStatemachine().getCurrentState().name());
            responseJson.addProperty("authAvailable", starter.getConnectionManager().isLeagueAuthDataAvailable());
            out.println(responseJson.toString());
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
