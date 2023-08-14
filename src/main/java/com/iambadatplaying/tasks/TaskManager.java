package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;


public class TaskManager {

    private MainInitiator mainInitiator;

    private HashMap<String, Task> runningtaskList;

    private HashMap<String, Task> allTasksMap;

    private boolean running = false;

    private Path taskDirPath;

    private TaskLoader taskLoader;

    public TaskManager(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void updateAllTasks(String message) {
        try {
            JSONArray updateArray = new JSONArray(message);
            for (Task task : runningtaskList.values()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            task.notify(updateArray);
                        } catch (Exception e) {
                            log(e.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
                        }
                    }
                }).start();
            }
        } catch (Exception e) {
            return;
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
        this.taskDirPath = mainInitiator.getTaskPath();
        loadDefaultTasks();
        taskLoader = new TaskLoader(mainInitiator);
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

    private void loadDefaultTasks() {
        log("Loading default tasks");
        addTaskToMap(new AutoAcceptQueue());
        addTaskToMap(new AutoPickChamp());
    }

    public void addTaskToMap(Task task) {
        if (task != null) {
            log("Added " + task.getClass().getSimpleName().toLowerCase()+ " to the Map");
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
                task.setMainInitiator(mainInitiator);
                task.init();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (String trigger :task.getTriggerApiEvents()) {
                            mainInitiator.getClient().getSocket().subscribeToEndpoint(trigger);
                        }
                    }
                }).start();
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
        for (Task task : runningtaskList.values()) {
            if (task != null) {
                task.shutdown();
            }
        }
        runningtaskList.clear();
    }

    public JSONArray getTaskAndArgs() {
        JSONArray taskList = new JSONArray();
        for (Task task : allTasksMap.values()) {
            JSONObject taskObject = new JSONObject();
            taskObject.put("name", task.getClass().getSimpleName());
            if (runningtaskList.get(task.getClass().getSimpleName().toLowerCase()) != null) {
                taskObject.put("running", task.isRunning());
            } else {
                taskObject.put("running", false);
            }
            taskObject.put("args", task.getRequiredArgs());
            taskList.put(taskObject);
        }
        return taskList;
    }

    public Task getTaskFromString(String name) {
        if (name != null && !name.isEmpty()) {
            name = name.toLowerCase();
            return allTasksMap.get(name);
        }
        return null;
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}
