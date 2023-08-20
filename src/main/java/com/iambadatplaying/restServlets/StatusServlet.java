package com.iambadatplaying.restServlets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class StatusServlet extends BaseRESTServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().println("--- Status ---");
        response.getWriter().println("MainInitiator running: " + mainInitiator.isRunning());
        response.getWriter().println("MainInitiator State: " + mainInitiator.getState().name());
        response.getWriter().println("MainInitiator auth available: " + mainInitiator.getConnectionManager().isLeagueAuthDataAvailable());
        response.getWriter().flush();
    }




}
