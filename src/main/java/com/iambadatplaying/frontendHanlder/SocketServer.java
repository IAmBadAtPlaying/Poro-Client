package com.iambadatplaying.frontendHanlder;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.ArrayList;

public class SocketServer {

    private MainInitiator mainInitiator;
    private final ArrayList<Session> sessions = new ArrayList<>();

    private Server server = null;

    public SocketServer(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public synchronized void removeSession(Session session) {
        sessions.remove(session);
    }

    public synchronized void sendToAllSessions(String message) {
        for(Session session: sessions) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mainInitiator.getFrontendMessageHandler().sendMessage(message,session);
                }
            }).start();
        }
    }

    public synchronized void addSession(Session session) {
        sessions.add(session);
    }

    public void init() {
        log("[Frontend] Setting up socket server");
        server = new Server();
        ServerConnector connector = new ServerConnector(server);

        connector.setReuseAddress(true);
        connector.setHost("0.0.0.0");
        connector.setPort(8887);

        server.addConnector(connector);

        WebSocketHandler wsHandler = new WebSocketHandler() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator((req, resp) -> new Socket(mainInitiator));
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
        sessions.clear();
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void log(String s, MainInitiator.LOG_LEVEL level) {
        MainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        MainInitiator.log(this.getClass().getName() +": " +s);
    }
}
