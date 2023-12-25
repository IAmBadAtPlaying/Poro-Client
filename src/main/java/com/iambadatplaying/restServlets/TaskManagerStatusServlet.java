package com.iambadatplaying.restServlets;

import com.google.gson.JsonObject;
import com.iambadatplaying.tasks.TaskManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TaskManagerStatusServlet extends BaseRESTServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        response.setHeader("Content-Type", "application/json");
        JsonObject responseJson = new JsonObject();

        TaskManager taskManager = mainInitiator.getTaskManager();
        if (taskManager == null) {
            response.setStatus(javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.addProperty("message", "TaskManager not referenced");
            responseJson.addProperty("httpStatus", javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } else {
            responseJson.addProperty("running", taskManager.isRunning());
            responseJson.addProperty("httpStatus", javax.servlet.http.HttpServletResponse.SC_OK);
        }
        response.getWriter().println(responseJson.toString());
    }
}
