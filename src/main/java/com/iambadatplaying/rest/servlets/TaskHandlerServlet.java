package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.tasks.Task;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TaskHandlerServlet extends BaseRESTServlet {

    // /rest/tasks/{SOME_NAME} => GET INFO
    // /rest/tasks/{SOME_NAME} => POST START; SET PARAMS
    // /rest/tasks/{SOME_NAME} => DELETE STOP
    // /rest/tasks/{SOME_NAME} => PUT RESTART; CHANGE PARAMS

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setHeader("Content-Type", "application/json");
        JsonObject responseJson = new JsonObject();



        String taskName = getTaskNameFromPathInfo(request.getPathInfo());
        if (handledInvalidTaskName(taskName, response)) return;
        Task task = starter.getTaskManager().getTaskFromString(taskName);
        response.setStatus(HttpServletResponse.SC_OK);
        responseJson.addProperty("httpStatus", HttpServletResponse.SC_OK);
        responseJson.addProperty("name", taskName);
        responseJson.addProperty("running", task.isRunning());
        responseJson.add("args", task.getRequiredArgs());
        response.getWriter().println(responseJson.toString());
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
       doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setHeader("Content-Type", "application/json");
        JsonObject responseJson = new JsonObject();

        log(request.getPathInfo());

        String taskName = getTaskNameFromPathInfo(request.getPathInfo());
        if (handledInvalidTaskName(taskName, response)) return;

        StringBuilder sb = new StringBuilder();
        String line;
        JsonObject json;
        while ((line = request.getReader().readLine() )!= null) {
            sb.append(line);
        }
        try {
            JsonElement element = JsonParser.parseString(sb.toString());
            json = element.getAsJsonObject();
        } catch (Exception e) {
            responseJson.addProperty("message", "Invalid JSON");
            responseJson.addProperty("httpStatus", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Task task = starter.getTaskManager().getTaskFromString(taskName);
        if (task == null) {
            responseJson.addProperty("message", "Task not found");
            responseJson.addProperty("httpStatus", HttpServletResponse.SC_BAD_REQUEST);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().println(responseJson.toString());
            return;
        }

        starter.getTaskManager().addTask(taskName);
        if(!starter.getTaskManager().getActiveTaskByName(taskName).setTaskArgs(json)) {
            responseJson.addProperty("message", "Task args invalid");
            responseJson.addProperty("httpStatus", HttpServletResponse.SC_BAD_REQUEST);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().println(responseJson.toString());
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        responseJson.addProperty("httpStatus", HttpServletResponse.SC_OK);
        responseJson.addProperty("name", taskName);
        responseJson.add("args", task.getTaskArgs());
        response.getWriter().println(responseJson.toString());
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setHeader("Content-Type", "application/json");
        JsonObject responseJson = new JsonObject();

        String taskName = getTaskNameFromPathInfo(request.getPathInfo());
        if (handledInvalidTaskName(taskName, response)) return;
        Task task = starter.getTaskManager().getActiveTaskByName(taskName);
        if (task == null) {
            responseJson.addProperty("message", "Task not running");
            responseJson.addProperty("httpStatus", HttpServletResponse.SC_BAD_REQUEST);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().println(responseJson.toString());
            return;
        }
        starter.getTaskManager().removeTask(taskName);

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private boolean handledInvalidTaskName(String taskName, HttpServletResponse response) throws IOException {
        JsonObject responseJson = new JsonObject();
        if (taskName == null || taskName.length() == 0) {
            JsonArray taskList = starter.getTaskManager().getTaskAndArgs();
            responseJson.add("tasks", taskList);
            responseJson.addProperty("httpStatus", HttpServletResponse.SC_OK);
            response.getWriter().println(responseJson.toString());
            return true;
        }
        if (starter.getTaskManager() == null || !starter.getTaskManager().isRunning()) {
            responseJson.addProperty("message", "TaskManager not running");
            responseJson.addProperty("httpStatus", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().println(responseJson.toString());
            return true;
        }
        Task task = starter.getTaskManager().getTaskFromString(taskName);
        if (task == null) {
            responseJson.addProperty("message", "Task not found");
            responseJson.addProperty("httpStatus", HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println(responseJson.toString());
            return true;
        }
        return false;
    }


    private String getTaskNameFromPathInfo(String pathInfo) {
        if (pathInfo != null && pathInfo.length() > 1) {
            String modifiedPathInfo = pathInfo.replaceFirst("/","").trim();
            String[] pathParts = modifiedPathInfo.split("/");
            String taskName = pathParts[pathParts.length - 1];
            for (int i = 0; i < pathParts.length; i++) {
                log(pathParts[i]);
            }

            return taskName;
        }
        return null;
    }
}