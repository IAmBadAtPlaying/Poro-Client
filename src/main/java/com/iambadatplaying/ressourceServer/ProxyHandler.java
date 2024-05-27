package com.iambadatplaying.ressourceServer;

import com.google.gson.JsonObject;
import com.iambadatplaying.ConnectionStatemachine;
import com.iambadatplaying.Starter;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.*;


public class ProxyHandler extends AbstractHandler {
    public static String STATIC_PROXY_PREFIX = "/static";

    private final Starter starter;
    private final Map<String, byte[]> resourceCache;
    private final Map<String, Map<String, List<String>>> headerCache;

    public ProxyHandler(Starter starter) {
        super();
        this.starter = starter;
        this.resourceCache = new HashMap<>();
        this.headerCache = new HashMap<>();
    }

    public void resetCache() {
        resourceCache.clear();
        headerCache.clear();
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
        if (starter.getResourceServer().filterRequest(httpServletRequest, httpServletResponse) && !Starter.isDev) {
            request.setHandled(true);
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (s == null) return;

        String requestedCURResource = s.trim();
        if (requestedCURResource.startsWith(STATIC_PROXY_PREFIX)) {
            requestedCURResource = requestedCURResource.substring(STATIC_PROXY_PREFIX.length()).trim();
            handleStatic(requestedCURResource, request, httpServletRequest, httpServletResponse);
        } else handleNormal(requestedCURResource, request, httpServletRequest, httpServletResponse, false);
    }

    public void handleStatic(String resource, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        byte[] cachedResource = resourceCache.get(resource);
        if (cachedResource != null) {
            Map<String, List<String>> cachedHeaders = headerCache.get(resource);
            serveResource(httpServletResponse, cachedResource,
                    cachedHeaders, true);
            request.setHandled(true);
            return;
        }
        handleNormal(resource, request, httpServletRequest, httpServletResponse, true);
    }

    public void handleNormal(String resource, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, boolean putToMap) throws IOException {
        InputStream is = null;


        if (Starter.getInstance().getConnectionStatemachine().getCurrentState() != ConnectionStatemachine.State.CONNECTED) {
            log("Tried to access ressource while connection is not established", Starter.LOG_LEVEL.ERROR);
            JsonObject response = new JsonObject();
            response.addProperty("error", "League of Legends not running");

            httpServletResponse.setContentType("application/json");
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            httpServletResponse.getWriter().write(
                    response.toString()
            );
            request.setHandled(true);
            return;
        }

        //Stop easy access to bearer tokens
        if (isAccessingProtectedResource(resource)) {
            JsonObject response = new JsonObject();
            response.addProperty("error", "Access to protected resource denied");
            response.addProperty("message",
                    "This resource is protected and cannot be accessed through the proxy. " +
                            "This is an intentional security measure to stop easy access to your bearer token. " +
                            "If you need to access this resource, please do so directly.");
            httpServletResponse.setContentType("application/json");
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpServletResponse.getWriter().write(
                    response.toString()
            );
            request.setHandled(true);
            return;
        }

        Optional<String> queryParameters = queryParametersToAppendString(httpServletRequest.getParameterMap());

        HttpURLConnection con = null;

        String postBody = "";

        try {
            StringBuilder requestBodyBuilder = new StringBuilder();
            BufferedReader reader = httpServletRequest.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                log(line);
                requestBodyBuilder.append(line);
            }
            postBody = requestBodyBuilder.toString();
        } catch (Exception e) {
            log("Error while reading request body: " + e.getMessage(), Starter.LOG_LEVEL.ERROR);
        }

        try {
            //Handle CORS preflight
            if ("OPTIONS".equals(request.getMethod())) {
                httpServletResponse.setStatus(200);
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH");
                request.setHandled(true);
                return;
            }

            if (queryParameters.isPresent()) {
                resource += "?" + queryParameters.get();
            }

            con = starter.getConnectionManager().buildConnection(ConnectionManager.conOptions.getByString(request.getMethod()), resource, postBody);
            if (con == null) {
                log("Cannot establish connection to " + resource + ", League might not be running", Starter.LOG_LEVEL.ERROR);
                return;
            }
            httpServletResponse.setContentType(con.getContentType());
            if (!starter.getConnectionManager().isLeagueAuthDataAvailable()) {
                return;
            }
            is = (InputStream) starter.getConnectionManager().getResponse(ConnectionManager.responseFormat.INPUT_STREAM, con);
            Map<String, List<String>> headers = con.getHeaderFields();

            if (is == null) {
                log(""+con.getResponseCode());
            }

            byte[] resourceBytes = readBytesFromStream(is);

            if (putToMap) {
                headerCache.put(resource, con.getHeaderFields());
                resourceCache.put(resource, resourceBytes);
            }

            serveResource(httpServletResponse, resourceBytes, headers, false);
            request.setHandled(true);
        } catch (Exception e) {
            log("Error while handling request for " + resource + ": " + e.getMessage(), Starter.LOG_LEVEL.ERROR);
            e.printStackTrace();
        } finally {
            if (is != null) {
                is.close();
            }
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private boolean isAccessingProtectedResource(String resource) {
        return ConnectionManager.isProtectedRessource(resource);
    }

    private Optional<String> queryParametersToAppendString(Map<String, String[]> params) {
        if (params == null || params.isEmpty()) return Optional.empty();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String key = entry.getKey();
            String[] value = entry.getValue();
            for (String s : value) {
                sb.append(URLEncoder.encode(key)).append("=").append(URLEncoder.encode(s)).append("&");
            }
        }

        //Remove last "&"
        sb.replace(sb.length() - 1, sb.length(), "");

        return Optional.of(sb.toString());
    }

    private byte[] readBytesFromStream(InputStream inputStream) throws IOException {
        if (inputStream == null) return new byte[0];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int bytesRead;
        byte[] data = new byte[8192];
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private void serveResource(HttpServletResponse response, byte[] resourceBytes, Map<String, List<String>> headers, boolean cached) {
        try {
            if (headers != null) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    List<String> value = entry.getValue();
                    if (key != null && value != null) {
                        switch (key) {
                            case "access-control-allow-origin":
                            case "Cache-Control":
                                continue;
                            default:
                                for (String s : value) {
                                    response.setHeader(key, s);
                                }
                                break;
                        }
                    }
                }
            }

            if (cached) {
                response.setHeader("Cache-Control", "public, immutable, max-age=604800, must-understand");
            }

            response.getOutputStream().write(resourceBytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log(e.getCause().getMessage(), Starter.LOG_LEVEL.ERROR);
        }
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getSimpleName() + ": " + s);
    }
}
