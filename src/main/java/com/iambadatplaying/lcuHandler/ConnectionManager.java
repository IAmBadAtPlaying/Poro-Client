package com.iambadatplaying.lcuHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.Starter;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.*;

public class ConnectionManager {
    public enum conOptions {
        GET ("GET"),
        POST ("POST"),
        PATCH("PATCH"),
        DELETE ("DELETE"),
        PUT ("PUT");

        final String name;
        conOptions(String name) {
            this.name = name;
        }

        public static conOptions getByString(String s) {
            if (s == null) return null;
            switch (s.toUpperCase()) {
                case "GET":
                    return conOptions.GET;
                case "POST":
                    return conOptions.POST;
                case "PATCH":
                    return conOptions.PATCH;
                case "DELETE":
                    return conOptions.DELETE;
                case "PUT":
                    return conOptions.PUT;
                default:
                    return null;
            }
        }
    }
    public enum responseFormat {
        STRING (0),
        INPUT_STREAM(1),
        RESPONSE_CODE(4);

        final Integer id;
        responseFormat(Integer id) {
            this.id = id;
        }
    }

    private String[] lockfileContents = null;
    private String authString = null;
    private String preUrl = null;
    private String port = null;
    private String riotAuthString = null;
    private String riotPort = null;
    private WebSocketClient client = null;
    private MainInitiator mainInitiator = null;

    private boolean leagueAuthDataAvailable = false;

    private Timer timer = null;

    private SSLContext sslContextGlobal = null;

    private HashMap<String, Integer> champHash = new HashMap<>();

    public ConnectionManager(MainInitiator mainInitiator) {
        this.preUrl = null;
        this.authString = null;
        this.mainInitiator = mainInitiator;
    }


    public void init() {
        allowHttpMethods("PATCH");
        if (!allowUnsecureConnections()) {
            return;
        }
        if (!getAuthFromProcess()) {
            log("Either missing permissions or League is not running", MainInitiator.LOG_LEVEL.INFO);
            log("Starting Backup Timer", MainInitiator.LOG_LEVEL.INFO);
            timer = new Timer();
            checkForProcess();
        } else leagueAuthDataAvailable = true;
    }

    private void checkForProcess() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                log("Checking for Process", MainInitiator.LOG_LEVEL.INFO);
                if (!mainInitiator.isRunning()) return;
                if (getAuthFromProcess()) {
                    log("Success getting Process Info", MainInitiator.LOG_LEVEL.INFO);
                    leagueAuthDataAvailable = true;
                    timer.cancel();
                } else {
                    log("No Success", MainInitiator.LOG_LEVEL.INFO);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    checkForProcess();
                                }
                            }, 2000);
                        }
                    }).start();
                }
            }
        };
        timer.schedule(timerTask, 0);
    }

    public boolean getAuthFromProcess() {
        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "wmic", "process","where","name=\"LeagueClientUx.exe\"","get","commandline");
        try {
            Process leagueUxProcess = processBuilder.start();
            String commandline = inputStreamToString(leagueUxProcess.getInputStream()).trim();
            if ("CommandLine".equals(commandline.trim())) {
                log("CommandLine returned no arguments -> Missing permissions, will exit", MainInitiator.LOG_LEVEL.ERROR);
                System.exit(Starter.ERROR_INSUFFICIENT_PERMISSIONS);
                return false;
            } else {
                String[] args = commandline.split("\" \"");
                if (args.length <= 1) return false;
                String portString = "--app-port=";
                String authString = "--remoting-auth-token=";
                String riotPortString = "--riotclient-app-port=";
                String riotAuthString = "--riotclient-auth-token=";
                for (int i = 0; i < args.length; i++) {
                    if (args[i].startsWith(portString)) {
                        String port = args[i].substring(portString.length());
                        log("Port: " +port, MainInitiator.LOG_LEVEL.INFO);
                        this.preUrl = "https://127.0.0.1:" + port;
                        this.port = port;
                    } else if (args[i].startsWith(authString)) {
                        String auth = args[i].substring(authString.length());
                        log("Auth: " + auth, MainInitiator.LOG_LEVEL.INFO);
                        this.authString = "Basic " + Base64.getEncoder().encodeToString(("riot:" + auth).trim().getBytes());
                        log("Auth Header: " +this.authString, MainInitiator.LOG_LEVEL.INFO);
                    } else if (args[i].startsWith(riotAuthString)) {
                        String riotAuth = args[i].substring(riotAuthString.length());
                        log("Riot Auth: " + riotAuth, MainInitiator.LOG_LEVEL.INFO);
                        this.riotAuthString = "Basic " + Base64.getEncoder().encodeToString(("riot:"+riotAuth).trim().getBytes());
                        log("Auth Header: " + this.riotAuthString, MainInitiator.LOG_LEVEL.INFO);
                    } else if (args[i].startsWith(riotPortString)) {
                        String riotPort = args[i].substring(riotPortString.length());
                        log("Riot Port: " + riotPort, MainInitiator.LOG_LEVEL.INFO);
                        this.riotPort = riotPort;
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isLoopbackAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean allowUnsecureConnections() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                            if (chain != null && chain.length > 0) {
                                String clientHost = chain[0].getSubjectX500Principal().getName();
                                if (isLoopbackAddress(clientHost) || "CN=rclient".equals(clientHost)) {
                                    return;
                                }
                            }
                            throw new CertificateException("Untrusted client certificate");
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                            if (chain != null && chain.length > 0) {
                                String serverHost = chain[0].getSubjectX500Principal().getName();
                                if (isLoopbackAddress(serverHost) || "CN=rclient".equals(serverHost)) {
                                    return;
                                }
                            }
                            throw new CertificateException("Untrusted server certificate");

                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            sslContextGlobal = SSLContext.getInstance("TLS");
            sslContextGlobal.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContextGlobal.getSocketFactory());
            return true;
        } catch (Exception e) {
            System.out.println(e);
            log("Unable to allow all Connections!", MainInitiator.LOG_LEVEL.ERROR);
        }
        return false;
    }


    public static String inputStreamToString(InputStream is) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

    private static void allowHttpMethods(String... methods) {
        try {
            Field declaredFieldMethods = HttpURLConnection.class.getDeclaredField("methods");
            Field declaredFieldModifiers = Field.class.getDeclaredField("modifiers");
            declaredFieldModifiers.setAccessible(true);
            declaredFieldModifiers.setInt(declaredFieldMethods, declaredFieldMethods.getModifiers() & ~Modifier.FINAL);
            declaredFieldMethods.setAccessible(true);
            String[] previous = (String[]) declaredFieldMethods.get(null);
            Set<String> current = new LinkedHashSet<>(Arrays.asList(previous));
            current.addAll(Arrays.asList(methods));
            String[] patched = current.toArray(new String[0]);
            declaredFieldMethods.set(null, patched);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public HttpsURLConnection buildConnection(conOptions options,String path , String post_body) {
        try {
            if (preUrl == null) {
                log("No preUrl", MainInitiator.LOG_LEVEL.ERROR);
                return null;
            }
            if (options == null) {
                log("No HTTP-Method provided", MainInitiator.LOG_LEVEL.ERROR);
            }
            URL clientLockfileUrl = new URL(preUrl + path);
            HttpsURLConnection con = (HttpsURLConnection) clientLockfileUrl.openConnection();
            if (con == null) {
                log(clientLockfileUrl.toString(), MainInitiator.LOG_LEVEL.ERROR);
                return null;
            }
            con.setRequestMethod(options.name);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", authString);
            switch (options) {
                case POST:
                case PUT:
                case PATCH:
                    if (post_body == null) {post_body = "";}
                    con.setDoOutput(true);
                    con.getOutputStream().write(post_body.getBytes(StandardCharsets.UTF_8));
                    break;
                default:
            }
            return con;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public HttpsURLConnection buildRiotConnection(conOptions options, String path, String post_body) {
        try {
            URL clientLockfileUrl = new URL("https://127.0.0.1:" + riotPort + path);
            HttpsURLConnection con = (HttpsURLConnection) clientLockfileUrl.openConnection();
            if (con == null) {
                log(clientLockfileUrl.toString(), MainInitiator.LOG_LEVEL.ERROR);
            }
            con.setRequestMethod(options.name);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", riotAuthString);
            switch (options) {
                case POST:
                case PUT:
                case PATCH:
                    if (post_body == null) {post_body = "";}
                    con.setDoOutput(true);
                    con.getOutputStream().write(post_body.getBytes(StandardCharsets.UTF_8));
                    break;
                default:
            }
            return con;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public HttpsURLConnection buildConnection(conOptions options, String path) {
        return buildConnection(options, path, null);
    }

    public Object getResponse(responseFormat respFormat, HttpURLConnection con) {
        if (con == null) return null;
        switch (respFormat) {
            case INPUT_STREAM:
                return handleInputStreamResponse(con);
            case RESPONSE_CODE:
                return handleResponseCode(con);
            case STRING:
            default:
                return handleStringResponse(con);
        }
    }

    public static JsonObject getResponseBodyAsJsonObject(HttpURLConnection con) {
        return handleJSONObjectResponse(con);
    }

    public static JsonArray getResponseBodyAsJsonArray(HttpURLConnection con) {
        return handleJSONArrayResponse(con);
    }

    private Integer handleResponseCode (HttpURLConnection con) {
        Integer responseCode = null;
        try {
            responseCode = con.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            con.disconnect();
        }
        return responseCode;
    }

    private InputStream handleInputStreamResponse (HttpURLConnection con) {
        InputStream is = null;
        try {
            is = con.getInputStream();
        } catch (Exception e) {
            try {
                is = con.getErrorStream();
            } catch (Exception ignored) {

            }
        }
        return is;
    }

    private static JsonObject handleJSONObjectResponse (HttpURLConnection con) {
        return toJsonObject(handleStringResponse(con));
    }

    private static JsonArray handleJSONArrayResponse (HttpURLConnection con) {

        return toJsonArray(handleStringResponse(con));
    }

    private static JsonArray toJsonArray(String s) {
        if(s == null) return null;
        return JsonParser.parseString(s).getAsJsonArray();
    }

    private static JsonObject toJsonObject(String s) {
        if(s == null) return null;
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static String handleStringResponse(HttpURLConnection conn) {
        String resp = null;
        try {
            if (100 <= conn.getResponseCode() && conn.getResponseCode() <= 399) {
                resp = inputStreamToString(conn.getInputStream());
            } else {
                resp = inputStreamToString(conn.getErrorStream());
            }
            conn.disconnect();
        } catch (Exception e) {
            return null;
        }
        return resp;
    }

    public boolean isLeagueAuthDataAvailable() {
        return leagueAuthDataAvailable;
    }

    public void shutdown() {
        if (timer != null) {
            timer.cancel();
        }
        if (champHash != null) {
            champHash.clear();
        }
        lockfileContents = null;
        preUrl = null;
        timer = null;
        leagueAuthDataAvailable = false;
        port = null;
        authString = null;
        riotPort = null;
        riotAuthString = null;
        sslContextGlobal = null;
        client = null;
    }

    public String getRiotAuth() {
        return riotAuthString;
    }

    public String getRiotPort() {
        return riotPort;
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getSimpleName() +": " +s);
    }

    public String getPort() {
        return port;
    }

    public String getPreUrl() {
        return preUrl;
    }

    public String getAuthString() {
        return authString;
    }

    public SSLContext getSslContextGlobal() {
        return sslContextGlobal;
    }
}
