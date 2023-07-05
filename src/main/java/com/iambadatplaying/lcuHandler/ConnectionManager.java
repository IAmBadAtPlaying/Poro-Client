package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.structs.LootElement;
import com.iambadatplaying.structs.Summoner;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.net.ssl.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
            switch (s) {
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
        SUMMONER (1),
        SUMMONER_ARRAY (2),
        LOOT (3),
        LOOT_ARRAY (4),
        IMAGE (5),
        INPUT_STREAM(6),
        JSON_OBJECT (7),
        JSON_ARRAY (8);

        final Integer id;
        responseFormat(Integer id) {
            this.id = id;
        }
    }

    public String conError = "Error: Connection cant be established";
    public String conRespError = "Error: Response Code Error";
    public String[] lockfileContents = null;
    public String authString = null;
    public String preUrl = null;
    public String port = null;
    public WebSocketClient client = null;
    public MainInitiator mainInitiator = null;

    private boolean leagueAuthDataAvailable = false;

    private Timer timer = null;

    public SSLContext sslContextGlobal = null;

    public HashMap<String, Integer> ChampHash = new HashMap<>();

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
                log("CommandLine returned no arguments -> Missing permissions", MainInitiator.LOG_LEVEL.INFO);
                return false;
            } else {
                String[] args = commandline.split("\" \"");
                if (args.length <= 1) return false;
                String portString = "--app-port=";
                String authString = "--remoting-auth-token=";
                for (int i = 0; i < args.length; i++) {
                    if (args[i].startsWith(portString)) {
                        String port = args[i].substring(portString.length());
                        log("Port: " +port, MainInitiator.LOG_LEVEL.INFO);
                        this.preUrl = "https://127.0.0.1:" + port;
                        this.port = port;
                    }
                    if (args[i].startsWith(authString)) {
                        String auth = args[i].substring(authString.length());
                        log("Auth: " + auth, MainInitiator.LOG_LEVEL.INFO);
                        this.authString = "Basic " + Base64.getEncoder().encodeToString(("riot:" + auth).trim().getBytes());
                        log("Auth Header: " +this.authString, MainInitiator.LOG_LEVEL.INFO);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    @Deprecated
    public boolean getAuthFromLocationFile() {
        InputStream is = null;
        File lockfile = null;
        try{
            File refLockfile = new File(mainInitiator.getBasePath()+"\\assets\\location-file");
            if(refLockfile.exists() && !refLockfile.isDirectory()) {
                Scanner reader = new Scanner(refLockfile);
                if(reader.hasNextLine()) {
                    lockfile = new File(reader.nextLine());
                }
            }
            if(lockfile == null) {
                log("League is not running, will not start!", MainInitiator.LOG_LEVEL.ERROR);
                return false;
            }
            is = new FileInputStream(lockfile);
            if (is == null) {
                return false;
            }
            String result = inputStreamToString(is);
            if (result != null) {
                this.lockfileContents = result.split(":");
            }
        } catch (IOException e) {
            log("No lockfile, League is probably not running", MainInitiator.LOG_LEVEL.ERROR);
            return false;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        log("Port: "+ lockfileContents[2], MainInitiator.LOG_LEVEL.INFO);
        log("Auth: "+ lockfileContents[3], MainInitiator.LOG_LEVEL.INFO);
        this.port = lockfileContents[2];
        this.preUrl = "https://127.0.0.1:" + lockfileContents[2];
        this.authString = "Basic " + Base64.getEncoder().encodeToString(("riot:" + lockfileContents[3]).trim().getBytes());
        log("Header Auth: " + authString, MainInitiator.LOG_LEVEL.INFO);
        return true;
    }

    public boolean allowUnsecureConnections() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
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


    public String inputStreamToString(InputStream is) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                result.append(line).append("\n");
            }
            br.close();
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
            URL clientLockfileUrl = new URL(preUrl + path);
            HttpsURLConnection con = (HttpsURLConnection) clientLockfileUrl.openConnection();
            if (con == null || !(con instanceof HttpsURLConnection)) {
                log(clientLockfileUrl.toString(), MainInitiator.LOG_LEVEL.ERROR);
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

    public HttpURLConnection buildConnection(conOptions options, String path) {
        try {
            URL clientLockfileUrl = new URL(preUrl + path);
            HttpURLConnection con = (HttpURLConnection) clientLockfileUrl.openConnection();
            if (con == null || !(con instanceof HttpURLConnection)) {
                log(clientLockfileUrl.toString(), MainInitiator.LOG_LEVEL.ERROR);
                return null;
            }
            con.setRequestMethod(options.name);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", authString);
            return con;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Object getResponse(responseFormat respFormat, HttpURLConnection con) {
        switch (respFormat) {
            case STRING:
                return handleStringResponse(con);
            case LOOT:

                break;
            case JSON_ARRAY:
                return handleJSONArrayResponse(con);
            case JSON_OBJECT:
                return handleJSONObjectResponse(con);
            case SUMMONER:
                return handleSummonerResponse(con);
            case SUMMONER_ARRAY:
                return handleSummonerArrayResponse(con);
            case LOOT_ARRAY:
                return handleLootArrayResponse(con);
            case IMAGE:
                return handleImageResponse(con);
            case INPUT_STREAM:
                return handleInputStreamResponse(con);
            default:
                return handleStringResponse(con);
        }
        return null;
    }

    private InputStream handleInputStreamResponse (HttpURLConnection con) {
        InputStream is = null;
        try {
            is = con.getInputStream();
        } catch (Exception e) {
            try {
                is = con.getErrorStream();
            } catch (Exception ex) {

            }
        }
        return is;
    }

    private LootElement[] handleLootArrayResponse (HttpURLConnection con) {
        String resp = null;
        JSONArray jsonLootArray = null;
        try {
            resp = handleStringResponse(con);
            jsonLootArray = toJsonArray(resp);
        } catch (Exception e) {
            log("Failed to handle Loot Array Response for connection", MainInitiator.LOG_LEVEL.ERROR);
            e.printStackTrace();
        }
        if (resp != null && jsonLootArray != null) {
            LootElement[] lootArray = new LootElement[jsonLootArray.length()];
            for (int i = 0; i < jsonLootArray.length(); i++) {
                LootElement loot = new LootElement(jsonLootArray.getJSONObject(i).getString("lootId"));
                loot.setCount(jsonLootArray.getJSONObject(i).getInt("count"));
                loot.setDisenchantLootName(jsonLootArray.getJSONObject(i).getString("disenchantLootName"));
                loot.setSplashPath(jsonLootArray.getJSONObject(i).getString("splashPath"));
                loot.setValue(jsonLootArray.getJSONObject(i).getInt("value"));
                lootArray[i] = loot;
            }
            return lootArray;
        }

        return null;
    }

    private JSONObject handleJSONObjectResponse (HttpURLConnection con) {
        return toJsonObject(handleStringResponse(con));
    }

    private Summoner[] handleSummonerArrayResponse(HttpURLConnection con) {
        String resp = null;
        JSONArray jsonSummonerArray = null;
        Summoner[] summonerArray = null;
        try {
            resp = handleStringResponse(con);
            jsonSummonerArray = toJsonArray(resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (resp != null && jsonSummonerArray != null) {
            summonerArray = new Summoner[jsonSummonerArray.length()];
            for (int i = 0, j = jsonSummonerArray.length(); i < j; i++ ) {
                Summoner summoner = new Summoner(jsonSummonerArray.getJSONObject(i).getString("puuid"));
                summoner.setSummonerId(jsonSummonerArray.getJSONObject(i).getBigInteger("summonerId"));
                summoner.setSummonerLevel(jsonSummonerArray.getJSONObject(i).getInt("summonerLevel"));
                summoner.setDisplayName(jsonSummonerArray.getJSONObject(i).getString("summonerName"));
                summoner.setInternalName(jsonSummonerArray.getJSONObject(i).getString("summonerInternalName"));
                summoner.setProfileIconId(jsonSummonerArray.getJSONObject(i).getInt("summonerIconId"));
                summoner.setFirstPositionPreference(jsonSummonerArray.getJSONObject(i).getString("firstPositionPreference"));
                summoner.setSecondPositionPreference(jsonSummonerArray.getJSONObject(i).getString("secondPositionPreference"));

                summonerArray[i] = summoner;
            }
        }
        return summonerArray;
    }

    private JSONArray handleJSONArrayResponse (HttpURLConnection con) {

        return toJsonArray(handleStringResponse(con));
    }

    private Summoner handleSummonerResponse(HttpURLConnection con) {
        String resp = null;
        JSONObject jsonSummoner = null;
        try {
            resp = handleStringResponse(con);
            jsonSummoner = toJsonObject(resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Summoner.fromJsonObject(jsonSummoner);
    }

    private BufferedImage handleImageResponse (HttpURLConnection con) {
        BufferedImage resp = null;
        try {
            InputStream is = con.getInputStream();
            resp = ImageIO.read(is);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resp;
    }

    private JSONArray toJsonArray(String s) {
        if(s == null) return null;
        return new JSONArray(s);
    }
    private JSONObject toJsonObject(String s) {
        if(s == null) return null;
        return new JSONObject(s);
    }

    public String handleStringResponse(HttpURLConnection conn) {
        String resp = null;
        try {
            if (100 <= conn.getResponseCode() && conn.getResponseCode() <= 399) {
                resp = inputStreamToString(conn.getInputStream());
            } else {
                resp = inputStreamToString(conn.getErrorStream());
            }
        } catch (Exception e) {
            log(e.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
            return null;
        }
        return resp;
    }

    public String inviteIntoLobby(String summonerId, String summonerName) {
            HttpURLConnection con = buildConnection(conOptions.POST, "/lol-lobby/v2/lobby/invitations" , "[{\"toSummonerId\": "+summonerId+",\"toSummonerName\":\""+summonerName+"\"}]");
            if (con == null) return conError;
            try {
                Integer respCode = con.getResponseCode();
                if(con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    log("inviteIntoLobby-Error: Expected HTTP_OKAY (200) got: " + respCode, MainInitiator.LOG_LEVEL.ERROR);
                    return "Error: Invitation failed";
                }
                return "Success! (WIP)";
            } catch (Exception e) {
                e.printStackTrace();
            }
        return conRespError;
    }

    public String updatePlayerPreferences(Integer firstEntry, Integer secondEntry, Integer thirdEntry, Integer titleId, Integer bannerId) {
        String reqBody;
        if(titleId == -1) {
            reqBody = "{\"challengeIds\":["+firstEntry+","+secondEntry+","+thirdEntry+"],\"title\" : \"\", \"bannerAccent\": \""+bannerId+"\"}";
        } else reqBody = "{\"challengeIds\":["+firstEntry+","+secondEntry+","+thirdEntry+"],\"title\" : \""+titleId+"\", \"bannerAccent\": \""+bannerId+"\"}";
        HttpURLConnection con = buildConnection(conOptions.POST, "/lol-challenges/v1/update-player-preferences", reqBody);
        if (con == null) return conError;
        try {
            Integer respCode = con.getResponseCode();
            con.disconnect();
            if(respCode== HttpURLConnection.HTTP_NO_CONTENT) {
                return "Success";
            }
            log("updatePlayerPreferences-Error: Expected HTTP_NO_CONTENT (204) but got: " + respCode, MainInitiator.LOG_LEVEL.ERROR);
            return "Error: You dont own either title or id";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return conRespError;
    }

    public boolean isLeagueAuthDataAvailable() {
        return leagueAuthDataAvailable;
    }

    public void shutdown() {
        if (timer != null) {
            timer.cancel();
        }
        if (ChampHash != null) {
            ChampHash.clear();
        }
        lockfileContents = null;
        preUrl = null;
        timer = null;
        leagueAuthDataAvailable = false;
        port = null;
        authString = null;
        sslContextGlobal = null;
        client = null;
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }

}
