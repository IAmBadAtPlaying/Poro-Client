package com.iambadatplaying.frontendHanlder;

import com.iambadatplaying.Starter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SocketServer {

    private final Starter starter;
    private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<Socket>());

    private Server server = null;

    public SocketServer(Starter starter) {
        this.starter = starter;
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
        server.setStopAtShutdown(true);
        ServerConnector connector = new ServerConnector(server);

        connector.setReuseAddress(true);
        connector.setHost("127.0.0.1");
        connector.setPort(Starter.FRONTEND_SOCKET_PORT);

        server.addConnector(connector);

        WebSocketHandler wsHandler = new WebSocketHandler() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator((req, resp) -> {
                    if (Starter.getInstance().getResourceServer().filterWebSocketRequest(req, resp)) {
                        return null;
                    }

                   return new Socket(starter);
                });
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
            log("Error while stopping socket server", Starter.LOG_LEVEL.ERROR);
            e.printStackTrace();
        }
    }

    public List<Socket> getSockets() {
        return sockets;
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() +": " + s, level);
    }

    private void log(String s) {
        log(s, Starter.LOG_LEVEL.DEBUG);
    }
}
