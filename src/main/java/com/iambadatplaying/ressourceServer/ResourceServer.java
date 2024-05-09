package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.Starter;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ResourceServer {

    private final Starter starter;

    private Server server;
    private ProxyHandler proxyHandler;
    private final ArrayList<String> allowedOrigins;
    private final Pattern localHostPattern;

    public ResourceServer(Starter starter) {
        this.starter = starter;
        allowedOrigins = new ArrayList<>();
        if (Starter.isDev) {
            localHostPattern = Pattern.compile("^(http://)?(localhost|127\\.0\\.0\\.1):("+Starter.RESSOURCE_SERVER_PORT+"|"+Starter.DEBUG_FRONTEND_PORT+"|"+ Starter.DEBUG_FRONTEND_PORT_V2+")(/)?$");
        } else {
            localHostPattern = Pattern.compile("^(http://)?(localhost|127\\.0\\.0\\.1):"+Starter.RESSOURCE_SERVER_PORT+"(/)?$");
        }
        addAllowedOrigins();
    }

    private void addAllowedOrigins() {

    }

    public void init() {
        server = new Server(Starter.RESSOURCE_SERVER_PORT);

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());
        connector.setPort(Starter.RESSOURCE_SERVER_PORT);

        int maxHeaderSize = 1_024 * 8;
        HttpConfiguration httpConfig = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
        httpConfig.setRequestHeaderSize(maxHeaderSize);

        proxyHandler = new ProxyHandler(starter);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setResourceBase(Starter.class.getResource("/html").toExternalForm());

        ResourceHandler userDataHandler = new ResourceHandler();
        userDataHandler.setDirectoriesListed(true);
        userDataHandler.setResourceBase(starter.getConfigLoader().getAppFolderPath().toAbsolutePath().toString());

        RESTContextHandler restContext = new RESTContextHandler(starter);
        restContext.setContextPath("/rest");

//        ContextHandler configContext = new ContextHandler();
//        configContext.setContextPath("/config");
//        configContext.setHandler(new ConfigHandler(mainInitiator));

        // Proxy-prefix to handle Proxy requests
        ContextHandler proxyContext = new ContextHandler();
        proxyContext.setContextPath("/proxy");
        proxyContext.setHandler(proxyHandler);

        // Static-prefix for static resources
        ContextHandler staticContext = new ContextHandler() {

            @Override
            public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                DispatcherType dispatch = baseRequest.getDispatcherType();
                boolean newContext = baseRequest.takeNewContext();
                try {
                    if (newContext) {
                        this.requestInitialized(baseRequest, request);
                    }

                    if (filterRequest(request, response)) {
                        baseRequest.setHandled(true);
                        response.sendError(404);
                        return;
                    }

                    if (dispatch == DispatcherType.REQUEST && this.isProtectedTarget(target)) {
                        baseRequest.setHandled(true);
                        response.sendError(404);
                        return;
                    }

                    this.nextHandle(target, baseRequest, request, response);
                } finally {
                    if (newContext) {
                        this.requestDestroyed(baseRequest, request);
                    }

                }

            }
        };
        staticContext.setContextPath("/static");
        staticContext.setHandler(resourceHandler);

        ContextHandler userDataContext = new ContextHandler() {
            @Override
            public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                DispatcherType dispatch = baseRequest.getDispatcherType();
                boolean newContext = baseRequest.takeNewContext();
                try {
                    if (newContext) {
                        this.requestInitialized(baseRequest, request);
                    }

                    if (filterRequest(request, response)) {
                        baseRequest.setHandled(true);
                        response.sendError(404);
                        return;
                    }

                    if (dispatch == DispatcherType.REQUEST && this.isProtectedTarget(target)) {
                        baseRequest.setHandled(true);
                        response.sendError(404);
                        return;
                    }

                    this.nextHandle(target, baseRequest, request, response);
                } finally {
                    if (newContext) {
                        this.requestDestroyed(baseRequest, request);
                    }

                }
            }
        };
        userDataContext.setContextPath("/config");
        userDataContext.setHandler(userDataHandler);

        HandlerList handlerList = new HandlerList();
        handlerList.addHandler(proxyContext);
        handlerList.addHandler(userDataContext);
//        handlerList.addHandler(configContext);
        handlerList.addHandler(staticContext);
        handlerList.addHandler(restContext);

        server.setHandler(handlerList);

        try {
            server.start();
        } catch (Exception e) {
            starter.log("Error starting resource server: " + e.getMessage(), Starter.LOG_LEVEL.ERROR);
        }
    }

    public void resetCachedData() {
        proxyHandler.resetCache();
    }

    public boolean filterWebSocketRequest(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
        String origin = req.getHeader("Origin");

        //Either local host OR non-browser request
        if (origin == null) {
            return false;
        }

        if (localHostPattern.matcher(origin).find() || allowedOrigins.contains(origin)) {
            return false;
        }

        return true;
    }

    public boolean filterRequest(HttpServletRequest req, HttpServletResponse resp) {
        String origin = req.getHeader("Origin");

        //Either local host OR non-browser request
        if (origin == null) {
            return false;
        }

        if (localHostPattern.matcher(origin).find() || allowedOrigins.contains(origin)) {
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
            return false;
        }

        return true;
    }

    public void shutdown() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        server = null;
    }
}
