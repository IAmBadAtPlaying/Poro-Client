package com.iambadatplaying.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.Starter;
import com.iambadatplaying.tasks.impl.AutoAcceptQueue;
import com.iambadatplaying.tasks.impl.AutoPickChamp;
import com.iambadatplaying.tasks.impl.PickReminderTask;
import com.iambadatplaying.tasks.impl.SuppressUx;

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

    public void activateTask(String taskName) {
        if (taskName == null || taskName.isEmpty()) return;
        taskName = taskName.toLowerCase();
        Task task = allTasksMap.get(taskName);
        activateTask(task);
    }

    public boolean activateTask(Task task) {
        if (!running || task == null) return false;
        if (runningtaskList.containsKey(task.getClass().getSimpleName().toLowerCase())) return false;
        task.setMainInitiator(starter);
        task.init();
        log("Added task: " + task.getClass().getSimpleName());
        runningtaskList.put(task.getClass().getSimpleName().toLowerCase(), task);
        return true;
    }

    public void shutdownTask(String taskName) {
        if (taskName == null || taskName.isEmpty()) return;
        taskName = taskName.toLowerCase();
        Task task = runningtaskList.get(taskName);
        shutdownTask(task);
    }

    public boolean shutdownTask(Task task) {
        if (!running || task == null) return false;
        log("Removed task: " + task.getClass().getSimpleName());
        task.shutdown();
        runningtaskList.remove(task.getClass().getSimpleName().toLowerCase());
        return true;
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
            taskList.add(task.getClass().getSimpleName());
        }
        return taskList;
    }

    public Task getTaskFromString(String name) {
        if (name == null || name.isEmpty()) return null;

        return allTasksMap.get(name.toLowerCase());
    }

    public TaskLoader getTaskLoader() {
        return taskLoader;
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
