package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ResourceServer {

    private final MainInitiator mainInitiator;

    private Server server;

    public ResourceServer(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void init() {
        server = new Server(MainInitiator.RESSOURCE_SERVER_PORT);

        ProxyHandler proxyHandler = new ProxyHandler(mainInitiator);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setResourceBase(MainInitiator.class.getResource("/html").toExternalForm());

        ResourceHandler userDataHandler = new ResourceHandler();
        userDataHandler.setDirectoriesListed(true);
        userDataHandler.setResourceBase(mainInitiator.getConfigLoader().getAppFolderPath().toAbsolutePath().toString());

        RESTContextHandler restContext = new RESTContextHandler(mainInitiator);
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
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    response.setHeader("Access-Control-Allow-Methods", "GET");
                    response.setHeader("Access-Control-Allow-Headers", "Content-Type");

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
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    response.setHeader("Access-Control-Allow-Methods", "GET");
                    response.setHeader("Access-Control-Allow-Headers", "Content-Type");

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
            mainInitiator.log("Error starting resource server: " + e.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
        }
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
