package com.iambadatplaying;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.iambadatplaying.lcuHandler.ConnectionManager;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater {

    private final static String GIT_API_URL = "https://api.github.com/repos/IAmBadAtPlaying/Poro-Client/releases/latest";

    private final static String GIT_KEY_TAG_NAME = "tag_name";
    private final static String GIT_KEY_ASSETS = "assets";
    private final static String GIT_KEY_BROWSER_DOWNLOAD_URL = "browser_download_url";
    private final static String GIT_KEY_CONTENT_TYPE = "content_type";
    private final static String GIT_KEY_NAME = "name";

    private final static String GIT_VALUE_CONTENT_TYPE = "application/java-archive";
    private final static String GIT_VALUE_NAME = "Poro-Client.jar";

    private final static String REGEX_VERSION = "v(\\d+)\\.(\\d+)\\.(\\d+)";
    private final static Pattern PATTERN_VERSION = Pattern.compile(REGEX_VERSION);

    private final static String CLIENT_FILENAME = "Poro-Client.jar";

    private final static String ENTRY_CLASS = "com.iambadatplaying.Starter";


    public Updater() {
    }

    public static void main(String[] args) {
        JsonObject apiData = fetchGithubApiInfo();
        if (updatesPresent(apiData)) {
            removePreviousVersions();
            downloadLatestVersion(apiData);
        }
        startApplication();
    }

    public static void startApplication() {
        try {
            String currentDirPath = new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent();
            Path clientPath = new File(currentDirPath + File.separator + CLIENT_FILENAME).toPath();

            if (!clientPath.toFile().exists() || clientPath.toFile().isDirectory()) {
                System.out.println("Could not find client file, will not start application");
                return;
            }

            System.out.println("Starting application");
            ClassLoader classLoader = URLClassLoader.newInstance(new URL[]{clientPath.toUri().toURL()});
            Class<?> clientClass = classLoader.loadClass(ENTRY_CLASS);

            clientClass.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
        } catch (Exception e) {
            System.out.println("Could not start application");
        }
    }

    public static void downloadLatestVersion(JsonObject apiData) {
        try {

            if (!apiData.has(GIT_KEY_ASSETS)) {
                System.out.println("Could not find assets in GitHub API response");
                return;
            }
            JsonArray assets = apiData.get(GIT_KEY_ASSETS).getAsJsonArray();
            String downloadUrl = null;
            for (int i = 0, len = assets.size(); i < len; i++) {
                JsonObject currentAsset = assets.get(i).getAsJsonObject();
                if (!currentAsset.has(GIT_KEY_CONTENT_TYPE)) {
                    System.out.println("Could not find content type in GitHub API response");
                    continue;
                }
                if (!currentAsset.has(GIT_KEY_NAME)) {
                    System.out.println("Could not find name in GitHub API response");
                    continue;
                }
                if (!currentAsset.has(GIT_KEY_BROWSER_DOWNLOAD_URL)) {
                    System.out.println("Could not find browser download url in GitHub API response");
                    continue;
                }

                String contentType = currentAsset.get(GIT_KEY_CONTENT_TYPE).getAsString();
                String name = currentAsset.get(GIT_KEY_NAME).getAsString();
                String browserDownloadUrl = currentAsset.get(GIT_KEY_BROWSER_DOWNLOAD_URL).getAsString();

                if (!GIT_VALUE_CONTENT_TYPE.equals(contentType)) {
                    System.out.println("Content type is not application/java-archive");
                    continue;
                }

                if (!GIT_VALUE_NAME.equals(name)) {
                    System.out.println("Name is not Poro-Client.jar");
                    continue;
                }

                System.out.println("Json data is valid, will download latest version");

                downloadUrl = browserDownloadUrl;
                break;
            }

            if (downloadUrl == null) {
                System.out.println("Could not find download url in GitHub API response");
                return;
            }

            String currentDirPath = new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent();
            Path clientPath = new File(currentDirPath + File.separator + CLIENT_FILENAME).toPath();

            System.out.println("Downloading latest version from: " + downloadUrl);
            URL url = new URL(downloadUrl);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode != 200) {
                System.out.println("Could not download latest version, response code: " + responseCode);
                return;
            }

            InputStream inputStream = connection.getInputStream();

            Files.copy(inputStream, clientPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Successfully downloaded latest version");
        } catch (Exception e) {

        }

    }

    public static JsonObject fetchGithubApiInfo() {
        try {
            URL url = new URL(GIT_API_URL);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return null;
            }
            return ConnectionManager.getResponseBodyAsJsonObject(connection);
        } catch (Exception e) {
            return null;
        }
    }

    public static void removePreviousVersions() {
        try {
            String currentDirPath = new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent();
            Path clientPath = new File(currentDirPath + File.separator + CLIENT_FILENAME).toPath();

            File clientFile = clientPath.toFile();
            if (!clientFile.exists() || clientFile.isDirectory()) {
                System.out.println("Could not find client file, will install latest version");
                return;
            }

            if (!clientFile.delete()) {
                System.out.println("Could not remove previous version");
                return;
            }

            System.out.println("Successfully removed previous version");
        } catch (Exception e) {
            System.out.println("Could not remove previous versions");
        }

    }

    public static boolean updatesPresent(JsonObject responseJson) {
        if (!responseJson.has(GIT_KEY_TAG_NAME)) {
            System.out.println("Could not find version in GitHub API response");
            return false;
        }
        String latestVersion = responseJson.get(GIT_KEY_TAG_NAME).getAsString();
        System.out.println("Latest version: " + latestVersion);
        Matcher matcher = PATTERN_VERSION.matcher(latestVersion);
        if (!matcher.matches()) {
            System.out.println("Could not parse version from GitHub API response");
            return false;
        }

        Integer latestMajor = Integer.parseInt(matcher.group(1));
        Integer latestMinor = Integer.parseInt(matcher.group(2));
        Integer latestPatch = Integer.parseInt(matcher.group(3));

        Integer[] currentVersion = getCurrentVersion();
        if (currentVersion.length == 0) {
            System.out.println("Something went wrong scanning the current Project hierarchy, will not update");
            return false;
        }

        Integer currentMajor = currentVersion[0];
        Integer currentMinor = currentVersion[1];
        Integer currentPatch = currentVersion[2];

        System.out.println("Current version: " + currentMajor + "." + currentMinor + "." + currentPatch);

        if (latestMajor == 0) {
            System.out.println("The latest version is still in development, please be aware of bugs!");
        }

        if (latestMajor > currentMajor) {
            System.out.println("A new major version is available! (" + latestVersion + ")");
            return true;
        }
        if (latestMinor > currentMinor) {
            System.out.println("A new minor version is available! (" + latestVersion + ")");
            return true;
        }
        if (latestPatch > currentPatch) {
            System.out.println("A new patch version is available! (" + latestVersion + ")");
            return true;
        }
        System.out.println("You are running the latest version! (" + latestVersion + ")");
        return false;
    }

    private static Integer[] getCurrentVersion() {
        Integer[] version = new Integer[3];
        String currentDirPath = null;
        try {
            currentDirPath = new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent();
        } catch (Exception e) {
            System.out.println("Could not get current directory path");
            return new Integer[0];
        }

        Path clientPath = new File(currentDirPath + File.separator + CLIENT_FILENAME).toPath();
        System.out.println("Client path: " + clientPath);
        if (!clientPath.toFile().exists() || clientPath.toFile().isDirectory()) {
            System.out.println("Could not find client file, will install latest version");
            return new Integer[]{0, 0, 0};
        }

        System.out.println("Client file exists, will check version");
        try {
            ClassLoader classLoader = URLClassLoader.newInstance(new URL[]{clientPath.toUri().toURL()});
            Class<?> clientClass = classLoader.loadClass(ENTRY_CLASS);

            Field versionMajorField = clientClass.getField("VERSION_MAJOR");
            Field versionMinorField = clientClass.getField("VERSION_MINOR");
            Field versionPatchField = clientClass.getField("VERSION_PATCH");

            int currentMajor = versionMajorField.getInt(versionMajorField);
            int currentMinor = versionMinorField.getInt(versionMinorField);
            int currentPatch = versionPatchField.getInt(versionPatchField);

            return new Integer[]{currentMajor, currentMinor, currentPatch};
        } catch (Exception e) {
            return new Integer[0];
        }
    }
}
