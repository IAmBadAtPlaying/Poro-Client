package com.iambadatplaying.restServlets;

import com.iambadatplaying.MainInitiator;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class BaseRESTServlet extends HttpServlet {
    protected MainInitiator mainInitiator;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    protected JSONObject getJsonFromRequestBody(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        String line;
        JSONObject json = new JSONObject();
        try {
            req.setCharacterEncoding(StandardCharsets.UTF_8.toString());
            while ((line = req.getReader().readLine() )!= null) {
                sb.append(line);
            }
            json = new JSONObject(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    protected String[] sliceAtSlash(String pathInfo) {
        if (pathInfo != null && pathInfo.length() > 1) {
            String path = pathInfo.substring(1); // remove leading slash
            String[] pathParts = path.split("/");
            return pathParts;
        }
        return new String[0];
    }

    @Override
    public void init() throws ServletException {
        super.init();
        mainInitiator = (MainInitiator) getServletContext().getAttribute("mainInitiator");
    }

    public void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + s, level);
    }

    @Override
    public void log(String s) {
        mainInitiator.log(this.getClass().getSimpleName() +": " +s);
    }
}
