package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.Starter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;


public class TaskManager {

    private final Starter starter;

    private HashMap<String, Task> runningtaskList;

    private HashMap<String, Task> allTasksMap;

    private boolean running = false;

    private Path taskDirPath;

    private TaskLoader taskLoader;

    public TaskManager(Starter starter) {
        this.starter = starter;
    }

    public void updateAllTasks(String message) {
        try {
            JsonArray updateArray = JsonParser.parseString(message).getAsJsonArray();
            for (Task task : runningtaskList.values()) {
                new Thread(() -> {
                    try {
                        task.notify(updateArray);
                    } catch (Exception e) {
                        log(e.getMessage(), Starter.LOG_LEVEL.ERROR);
                    }
                }).start();
            }
        } catch (Exception e) {
        }
    }

    public Path getTaskDir() {
        return taskDirPath;
    }

    //TODO: Load class files, don't compile again
    //TODO: Load tasks after websocket connection; Init can be done before
    public void init() {
        this.runningtaskList = new HashMap<>();
        this.allTasksMap = new HashMap<>();
        this.taskDirPath = starter.getTaskPath();
        loadDefaultTasks();
        taskLoader = new TaskLoader(starter);
        taskLoader.setTaskDirectory(taskDirPath);
        taskLoader.init();
        running = true;
        log("Initialized");
    }

//    public void startDefaultTasks() {
//        log("Starting default tasks");
//        addTask("AutoAcceptQueue");
//        addTask("AutoPickChamp");
//    }

    private Path getTaskPath() {
        return Paths.get(starter.getBasePath().toString() + "/tasks");
    }

    private void loadDefaultTasks() {
        log("Loading default tasks");
        addTaskToMap(new AutoAcceptQueue());
        addTaskToMap(new AutoPickChamp());
        addTaskToMap(new PickReminderTask());
        addTaskToMap(new SuppressUx());
    }

    public void addTaskToMap(Task task) {
        if (task != null) {
            log("Added " + task.getClass().getSimpleName().toLowerCase() + " to the Map");
            allTasksMap.put(task.getClass().getSimpleName().toLowerCase(), task);
        }
    }

    public void loadAtRuntime(String taskName) {
        Path taskPath = Paths.get(taskDirPath.toString() + "/" + taskName);
        taskLoader.compileAndLoadTask(taskPath);
    }

    public void addTask(String taskName) {
        if (running && taskName != null && !taskName.isEmpty()) {
            taskName = taskName.toLowerCase();
            runningtaskList.computeIfAbsent(taskName, k -> {
                Task task = allTasksMap.get(k);
                if (task == null) return null;
                task.setMainInitiator(starter);
                task.init();
                log("Added task: " + task.getClass().getSimpleName());
                return task;
            });
        }
    }

    public void removeTask(String taskName) {
        if (running && taskName != null && !taskName.isEmpty()) {
            taskName = taskName.toLowerCase();
            runningtaskList.computeIfPresent(taskName, (k, v) -> {
                log("Removed task: " + k);
                v.shutdown();
                return null;
            });
        }
    }

    public boolean isRunning() {
        return running;
    }

    public Task getActiveTaskByName(String name) {
        if (name != null && !name.isEmpty()) {
            name = name.toLowerCase();
            return runningtaskList.get(name);
        }
        return null;
    }

    public synchronized void shutdown() {
        running = false;
        if (runningtaskList == null) return;
        for (Task task : runningtaskList.values()) {
            if (task != null) {
                task.shutdown();
            }
        }
        runningtaskList.clear();
    }

    public JsonArray getTaskAndArgs() {

        JsonArray taskList = new JsonArray();
        if (!running) return taskList;
        for (Task task : allTasksMap.values()) {
            JsonObject taskObject = new JsonObject();
            taskObject.addProperty("name", task.getClass().getSimpleName());
            if (runningtaskList.get(task.getClass().getSimpleName().toLowerCase()) != null) {
                taskObject.addProperty("running", task.isRunning());
            } else {
                taskObject.addProperty("running", false);
            }
            taskObject.add("args", task.getRequiredArgs());
            taskList.add(taskObject);
        }
        return taskList;
    }

    public Task getTaskFromString(String name) {
        if (name == null || name.isEmpty()) return null;

        return allTasksMap.get(name.toLowerCase());
    }

    public Collection<Task> getRunningTasks() {
        return runningtaskList.values();
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getSimpleName() + ": " + s);
    }

}
