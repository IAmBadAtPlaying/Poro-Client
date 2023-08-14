package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ResourceServer {

    private MainInitiator mainInitiator;

    private Server server;

    public ResourceServer(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void init() {
        server = new Server(35199);

        ProxyHandler proxyHandler = new ProxyHandler(mainInitiator);

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setResourceBase(MainInitiator.class.getResource("/html").toExternalForm());


        RESTContextHandler restContext = new RESTContextHandler(mainInitiator);
        restContext.setContextPath("/rest");

        ContextHandler configContext = new ContextHandler();
        configContext.setContextPath("/config");
        configContext.setHandler(new ConfigHandler(mainInitiator));

        // Proxy-prefix to handle Proxy requests
        ContextHandler proxyContext = new ContextHandler();
        proxyContext.setContextPath("/proxy");
        proxyContext.setHandler(proxyHandler);

        // Static-prefix for static resources
        ContextHandler staticContext = new ContextHandler() {

            @Override
            public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                DispatcherType dispatch = baseRequest.getDispatcherType();
                boolean new_context = baseRequest.takeNewContext();
                try {
                    if (new_context) {
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
                    if (new_context) {
                        this.requestDestroyed(baseRequest, request);
                    }

                }

            }
        };
        staticContext.setContextPath("/static");
        staticContext.setHandler(resourceHandler);

        HandlerList handlerList = new HandlerList();
        handlerList.addHandler(proxyContext);
        handlerList.addHandler(configContext);
        handlerList.addHandler(staticContext);
        handlerList.addHandler(restContext);

        server.setHandler(handlerList);

        try {
            server.start();
        } catch (Exception e) {

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
