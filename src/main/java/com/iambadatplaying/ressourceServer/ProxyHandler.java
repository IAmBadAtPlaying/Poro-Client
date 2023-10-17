package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProxyHandler extends AbstractHandler {
    public static String STATIC_PROXY_PREFIX = "/static";

    private final MainInitiator mainInitiator;
    private final Map<String, byte[]> resourceCache;
    private final Map<String, Map<String, List<String>>> headerCache;

    public ProxyHandler(MainInitiator mainInitiator) {
        super();
        this.mainInitiator = mainInitiator;
        this.resourceCache = new HashMap<>();
        this.headerCache = new HashMap<>();
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
            httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH");
            httpServletResponse.setHeader("Access-Control-Allow-Headers", "Content-Type");
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
            serveResource(httpServletResponse, cachedResource, cachedHeaders);
            request.setHandled(true);
            return;
        }
        handleNormal(resource, request, httpServletRequest, httpServletResponse, true);
    }

    public void handleNormal(String resource, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, boolean putToMap) throws IOException {
        InputStream is = null;
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
            log("Error while reading request body: " + e.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
        }

        try {
            if ("OPTIONS".equals(request.getMethod())) {
                httpServletResponse.setStatus(200);
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH");
                request.setHandled(true);
                return;
            }
            con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.getByString(request.getMethod()), resource, postBody);
            if (con == null) {
                log("Cannot establish connection to " + resource + ", League might not be running", MainInitiator.LOG_LEVEL.ERROR);
                return;
            }
            httpServletResponse.setContentType(con.getContentType());
            if (!mainInitiator.getConnectionManager().isLeagueAuthDataAvailable()) {
                return;
            }
            is = (InputStream) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.INPUT_STREAM, con);
            Map<String, List<String>> headers = con.getHeaderFields();


            byte[] resourceBytes = readBytesFromStream(is);

            if (putToMap) {
                headerCache.put(resource, con.getHeaderFields());
                resourceCache.put(resource, resourceBytes);
            }

            serveResource(httpServletResponse, resourceBytes, headers);
            request.setHandled(true);
        } catch (Exception e) {
            log("Error while handling request for " + resource + ": " + e.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
        } finally {
            if (is != null) {
                is.close();
            }
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private byte[] readBytesFromStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int bytesRead;
        byte[] data = new byte[4096];
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private void serveResource(HttpServletResponse response, byte[] resourceBytes, Map<String, List<String>> headers) throws IOException {
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String key = entry.getKey();
                List<String> value = entry.getValue();
                if (key != null && value != null) {
                    if ("Access-Control-Allow-Origin".equalsIgnoreCase(key)) continue;
                    for (String s : value) {
                        response.setHeader(key, s);
                    }
                }
            }
        }

        response.getOutputStream().write(resourceBytes);
        response.getOutputStream().flush();
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getSimpleName() +": " +s);
    }
}
