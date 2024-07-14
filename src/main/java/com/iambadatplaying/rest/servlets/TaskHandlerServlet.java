package com.iambadatplaying.rest.servlets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iambadatplaying.Starter;
import com.iambadatplaying.tasks.Task;
import com.iambadatplaying.tasks.TaskManager;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

/**
 * Servlet that handles tasks
 */
@Path("/tasks")
public class TaskHandlerServlet {

    @GET
    @Produces("application/json")
    public Response getAvailableTasks() {
        Starter starter = Starter.getInstance();
        TaskManager taskManager = starter.getTaskManager();
        if (taskManager == null) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not referenced", "TaskManager not referenced, please wait and try again"))
                    .build();
        }

        if (!taskManager.isRunning()) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not running", "TaskManager not running, please wait for League to start and try again"))
                    .build();
        }

        return Response.status(Response.Status.OK).entity(taskManager.getTaskAndArgs()).build();
    }

    /**
     * Get the status and current configuration of a task by name
     */
    @GET
    @Produces("application/json")
    @Path("/{taskName}")
    public Response getTask(@PathParam("taskName") String taskName) {
        Starter starter = Starter.getInstance();
        TaskManager taskManager = starter.getTaskManager();
        if (taskManager == null) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not referenced", "TaskManager not referenced, please wait and try again"))
                    .build();
        }

        if (!taskManager.isRunning()) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not running", "TaskManager not running, please wait for League to start and try again"))
                    .build();
        }

        Task task = taskManager.getTaskFromString(taskName);
        if (task == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(ServletUtils.createErrorJson("Task not found", "Task with name " + taskName + " not found"))
                    .build();
        }

        JsonObject taskJson = new JsonObject();
        taskJson.addProperty("name", task.getClass().getSimpleName());
        taskJson.addProperty("running", task.isRunning());
        taskJson.add("args", task.getRequiredArgs());
        return Response.status(Response.Status.OK).entity(taskJson.toString()).build();
    }

    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/{taskName}")
    public Response startTask(@PathParam("taskName") String taskName, JsonElement jsonElement) {
        Starter starter = Starter.getInstance();
        TaskManager taskManager = starter.getTaskManager();
        if (taskManager == null) {
            return Response.status(Response
                            .Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not referenced", "TaskManager not referenced, please wait and try again"))
                    .build();
        }

        if (!taskManager.isRunning()) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not running", "TaskManager not running, please wait for League to start and try again"))
                    .build();
        }

        Task task = taskManager.getTaskFromString(taskName);
        if (task == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(ServletUtils.createErrorJson("Task not found", "Task with name " + taskName + " not found"))
                    .build();
        }

        if (!taskManager.activateTask(task)) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Failed to activate task", "Task failed to activate"))
                    .build();
        }

        if (jsonElement == null) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "All Data should be a JSON object"))
                    .build();
        }

        if (!jsonElement.isJsonObject()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "All Data should be a JSON object"))
                    .build();
        }

        JsonObject json = jsonElement.getAsJsonObject();
        boolean success = task.setTaskArgs(json);
        if (!success) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Failed to set task arguments", "Task failed to set arguments"))
                    .build();
        }

        return Response.status(Response.Status.OK).build();
    }

    @PUT
    @Path("/{taskName}")
    public Response modifyTask(@PathParam("taskName") String taskName, JsonElement body) {
        Starter starter = Starter.getInstance();
        TaskManager taskManager = starter.getTaskManager();
        if (taskManager == null) {
            return Response.status(Response
                            .Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not referenced", "TaskManager not referenced, please wait and try again"))
                    .build();
        }

        if (!taskManager.isRunning()) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not running", "TaskManager not running, please wait for League to start and try again"))
                    .build();
        }

        Task task = taskManager.getActiveTaskByName(taskName);
        if (task == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(ServletUtils.createErrorJson("Task not found", "Task with name " + taskName + " not found"))
                    .build();
        }

        if (body == null) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "All Data should be a JSON object"))
                    .build();
        }

        if (!body.isJsonObject()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid JSON", "All Data should be a JSON object"))
                    .build();
        }

        JsonObject json = body.getAsJsonObject();
        boolean success = task.setTaskArgs(json);
        if (!success) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ServletUtils.createErrorJson("Failed to set task arguments", "Task failed to set arguments"))
                    .build();
        }

        return Response.status(Response.Status.OK).build();
    }

    @DELETE
    @Path("/{taskName}")
    public Response stopTask(@PathParam("taskName") String taskName) {
        Starter starter = Starter.getInstance();
        TaskManager taskManager = starter.getTaskManager();
        if (taskManager == null) {
            return Response.status(Response
                            .Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not referenced", "TaskManager not referenced, please wait and try again"))
                    .build();
        }

        if (!taskManager.isRunning()) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ServletUtils.createErrorJson("TaskManager not running", "TaskManager not running, please wait for League to start and try again"))
                    .build();
        }

        if (taskName == null) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ServletUtils.createErrorJson("Invalid task name", "Task name cannot be null"))
                    .build();
        }

        Task task = taskManager.getActiveTaskByName(taskName.toLowerCase());
        if (task == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(ServletUtils.createErrorJson("Task not found", "Task with name " + taskName + " not found"))
                    .build();
        }

        taskManager.shutdownTask(task);
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
