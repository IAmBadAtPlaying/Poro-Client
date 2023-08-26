package com.iambadatplaying.tasks;

import com.iambadatplaying.MainInitiator;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//TODO: JRE cant compile java files, therefore allow users to upload .class files, no longer .java files
public class TaskLoader {

    private MainInitiator mainInitiator;
    private Path taskDirPath;

    public TaskLoader(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void setTaskDirectory(Path taskDirPath) {
        this.taskDirPath = taskDirPath;
    }

    public void init() {
        if (taskDirPath == null) {
            log("Task directory not set.", MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        if (!Files.exists(taskDirPath)) {
            log("Task directory does not exist.", MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        if (!Files.isDirectory(taskDirPath)) {
            log("Task directory is not a directory.", MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        File[] taskFiles = taskDirPath.toFile().listFiles();
        if (taskFiles == null) {
            log("Failed to list files in task directory.", MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        for (File taskFile : taskFiles) {
            if (taskFile.isFile()) {
                if (taskFile.getName().endsWith(".class")) {
                    log("Attempting to load task: " + taskFile.getName(), MainInitiator.LOG_LEVEL.INFO);
                    loadTask(taskFile.toPath());
                } else if (taskFile.getName().endsWith(".java")) {
                    log("Skipping: " + taskFile.getName(), MainInitiator.LOG_LEVEL.INFO);
                    compileAndLoadTask(taskFile.toPath());
                } else {
                    log("Ignoring file: " + taskFile.getName(), MainInitiator.LOG_LEVEL.INFO);
                }
            }
        }
    }

    public void compileTask(Path taskJavaPath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return;

        int compilationResult = compiler.run(null, null, null, taskJavaPath.toString());
        if (compilationResult != 0) {
            log("Could not compile task: " + taskJavaPath.toString(), MainInitiator.LOG_LEVEL.ERROR);
        }
    }

    public void loadTask(Path taskClassPath) {
        File classFile = taskClassPath.toFile();
        CustomClassLoader classLoader = new CustomClassLoader();
        Class<?> taskClass;
        try {
            taskClass = classLoader.loadClass(classFile);
        } catch (Exception e) {
            log("Failed to load task class from file: " + classFile.getName(), MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        Object taskInstance;
        try {
            taskInstance = taskClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log("Failed to instantiate task class from file: " + classFile.getName(), MainInitiator.LOG_LEVEL.ERROR);
            return;
        }

        if (taskInstance instanceof Task) {
            Task task = (Task) taskInstance;
            mainInitiator.getTaskManager().addTaskToMap(task);
        } else {
            log("The class in file: " + classFile.getName() + " does not implement Task interface.", MainInitiator.LOG_LEVEL.ERROR);
        }
    }

    public void compileAndLoadTask(Path taskPath) {
        compileTask(taskPath);
        deleteJavaFile(taskPath);

        String classFileName = taskPath.getFileName().toString().replace(".java", ".class");
        loadTask(taskPath.getParent().resolve(classFileName));
    }

    private void deleteJavaFile(Path taskJavaPath) {
        try {
            Files.delete(taskJavaPath);
        } catch (IOException e) {
            log("Failed to delete task java file: " + taskJavaPath.toString(), MainInitiator.LOG_LEVEL.ERROR);
        }
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        log(s, MainInitiator.LOG_LEVEL.DEBUG);
    }


    private static class CustomClassLoader extends ClassLoader {
        public Class<?> loadClass(File classFile) throws ClassNotFoundException, IOException {
            byte[] classBytes = Files.readAllBytes(classFile.toPath());
            return defineClass(null, classBytes, 0, classBytes.length);
        }
    }
}
