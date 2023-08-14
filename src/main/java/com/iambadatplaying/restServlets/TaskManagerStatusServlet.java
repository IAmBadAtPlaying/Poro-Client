package com.iambadatplaying.restServlets;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.tasks.TaskManager;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TaskManagerStatusServlet extends BaseRESTServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
        response.setHeader("Content-Type", "application/json");
        JSONObject responseJson = new JSONObject();

        TaskManager taskManager = mainInitiator.getTaskManager();
        if (taskManager == null) {
            response.setStatus(javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.put("message", "TaskManager not referenced");
            responseJson.put("httpStatus", javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } else {
            responseJson.put("running", taskManager.isRunning());
            responseJson.put("httpStatus", javax.servlet.http.HttpServletResponse.SC_OK);
        }
        response.getWriter().println(responseJson.toString());
    }
}
