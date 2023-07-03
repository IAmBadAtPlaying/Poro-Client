package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.tasks.AutoAcceptQueue;
import com.iambadatplaying.tasks.AutoPickChamp;
import org.json.JSONArray;

import java.util.HashMap;


public class TaskManager {

    private MainInitiator mainInitiator;

    private HashMap<String, Task> runningtaskList;

    private static HashMap<String, Task> allTasksMap;

    private boolean running = false;

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

    public void init() {
        this.runningtaskList = new HashMap<>();
        allTasksMap = new HashMap<>();
        log("Initialized");
        populateAllTasksMap();
        running = true;
    }

    private void populateAllTasksMap() {
        addTaskToMap(new AutoAcceptQueue());
        addTaskToMap(new AutoPickChamp());
    }

    private void addTaskToMap(Task task) {
        if (task != null) {
            log("Added " + task.getClass().getSimpleName()+ " to the Map");
            allTasksMap.put(task.getClass().getSimpleName(), task);
        }
    }

    public void addTask(String taskName) {
        if (running && taskName != null && !taskName.isEmpty()) {
            runningtaskList.computeIfAbsent(taskName, k -> {
                Task task = allTasksMap.get(k);
                task.setMainInitiator(mainInitiator);
                task.init();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println();
                        while (mainInitiator.getClient().getSocket() == null || !mainInitiator.getClient().getSocket().isConnected()) {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
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
            runningtaskList.computeIfPresent(taskName, (k, v) -> {
                log("Removed task: " + k);
                v.shutdown();
                return null;
            });
        }
    }

    public Task getActiveTaskByName(String name) {
        return runningtaskList.get(name);
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

    public static Task getTaskFromString(String name) {
        return allTasksMap.get(name);
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}
