package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.lcuHandler.ConnectionManager;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.*;

public class ProxyHandler extends AbstractHandler {
    public static String STATIC_PROXY_PREFIX = "/static";

    private MainInitiator mainInitiator;
    private Map<String, byte[]> resourceCache;

    public ProxyHandler(MainInitiator mainInitiator) {
        super();
        this.mainInitiator = mainInitiator;
        this.resourceCache = new HashMap<>();
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
            httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET");
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
            serveResource(httpServletResponse, cachedResource);
            request.setHandled(true);
            return;
        }
        handleNormal(resource, request, httpServletRequest, httpServletResponse, true);
    }

    public void handleNormal(String resource, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, boolean putToMap) throws IOException {
        InputStream is = null;
        HttpURLConnection con = null;

        try {
            con = mainInitiator.getConnectionManager().buildConnection(ConnectionManager.conOptions.GET, resource, null);
            httpServletResponse.setContentType(con.getContentType());
            if (!mainInitiator.getConnectionManager().isLeagueAuthDataAvailable()) {
                return;
            }
            is = (InputStream) mainInitiator.getConnectionManager().getResponse(ConnectionManager.responseFormat.INPUT_STREAM, con);
            Map<String, List<String>> headers = con.getHeaderFields();
            if (headers != null) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    List<String> value = entry.getValue();
                    if (key != null && value != null) {
                        for (String s : value) {
                            httpServletResponse.setHeader(key, s);
                        }
                    }
                }
            }

            byte[] resourceBytes = readBytesFromStream(is);

            if (putToMap) {
                resourceCache.put(resource, resourceBytes);
            }

            serveResource(httpServletResponse, resourceBytes);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                is.close();
            }
            if (con != null) {
                con.disconnect();
            }
        }
        request.setHandled(true);
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

    private void serveResource(HttpServletResponse response, byte[] resourceBytes) throws IOException {
        response.getOutputStream().write(resourceBytes);
        response.getOutputStream().flush();
    }
}
