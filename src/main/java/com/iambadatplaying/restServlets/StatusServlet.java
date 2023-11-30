package com.iambadatplaying.restServlets;

import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;


public class StatusServlet extends BaseRESTServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = response.getWriter()) {
            JSONObject responseJson = new JSONObject();
            responseJson.put("running", mainInitiator.isRunning());
            responseJson.put("state", mainInitiator.getState().name());
            responseJson.put("authAvailable", mainInitiator.getConnectionManager().isLeagueAuthDataAvailable());
            out.println(responseJson.toString());
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
