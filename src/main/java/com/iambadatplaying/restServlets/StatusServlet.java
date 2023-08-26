package com.iambadatplaying.restServlets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;


public class StatusServlet extends BaseRESTServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = response.getWriter()) {
            out.println("--- Status ---");
            out.println("MainInitiator running: " + mainInitiator.isRunning());
            out.println("MainInitiator State: " + mainInitiator.getState().name());
            out.println("MainInitiator auth available: " + mainInitiator.getConnectionManager().isLeagueAuthDataAvailable());
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
