package com.iambadatplaying.frontendHanlder;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SocketServer {

    private final MainInitiator mainInitiator;
    private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<Socket>());

    private Server server = null;

    public SocketServer(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public synchronized void removeSocket(Socket socket) {
        sockets.remove(socket);
    }

    public void sendToAllSessions(String message) {
        for(Socket socket: sockets) {
            socket.sendMessage(message);
        }
    }

    public synchronized void addSocket(Socket socket) {
        sockets.add(socket);
    }

    public void init() {
        log("[Frontend] Setting up socket server");
        server = new Server();
        ServerConnector connector = new ServerConnector(server);

        connector.setReuseAddress(true);
        connector.setHost("127.0.0.1");
        connector.setPort(MainInitiator.FRONTEND_SERVER_PORT);

        server.addConnector(connector);

        WebSocketHandler wsHandler = new WebSocketHandler() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator((req, resp) -> new Socket(mainInitiator));
                log("[Frontend] Configured socket server");
            }
        };
        server.setHandler(wsHandler);
        try {
            server.start();
            log("[Frontend] Socket server started");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        try {
            for (Socket socket: sockets) {
                if (socket == null) continue;
                socket.externalShutdown();
            }
            sockets.clear();
            server.stop();
        } catch (Exception e) {
            log("Error while stopping socket server", MainInitiator.LOG_LEVEL.ERROR);
            e.printStackTrace();
        }
    }


    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getSimpleName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }
}
