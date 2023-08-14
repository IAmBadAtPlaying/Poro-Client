package com.iambadatplaying.restServlets;

import com.iambadatplaying.MainInitiator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class BaseRESTServlet extends HttpServlet {
    protected MainInitiator mainInitiator;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    public void init() throws ServletException {
        super.init();
        mainInitiator = (MainInitiator) getServletContext().getAttribute("mainInitiator");
    }

    public void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    @Override
    public void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }
}
