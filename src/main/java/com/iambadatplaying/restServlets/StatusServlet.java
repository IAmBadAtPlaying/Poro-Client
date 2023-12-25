package com.iambadatplaying.restServlets;

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
            responseJson.addProperty("running", mainInitiator.isRunning());
            responseJson.addProperty("state", mainInitiator.getState().name());
            responseJson.addProperty("authAvailable", mainInitiator.getConnectionManager().isLeagueAuthDataAvailable());
            out.println(responseJson.toString());
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
