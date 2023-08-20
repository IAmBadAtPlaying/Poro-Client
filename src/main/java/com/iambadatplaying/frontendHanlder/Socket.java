package com.iambadatplaying.frontendHanlder;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.IOException;
import java.util.TimerTask;

@WebSocket
public class Socket {

    private MainInitiator mainInitiator;

    private TimerTask timerTask;

    public Socket(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
        mainInitiator.log("[Frontend] Socket created", MainInitiator.LOG_LEVEL.DEBUG);
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("Client connected: " + session.getRemoteAddress().getAddress());
        createNewKeepAlive(session);
        mainInitiator.getServer().addSession(session);
        try {
            session.getRemote().sendString("[]");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createNewKeepAlive(Session s) {
        new java.util.Timer().schedule(
                this.timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            s.getRemote().sendString("[]");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        createNewKeepAlive(s);
                    }
                }
                ,
                290000
        );
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        mainInitiator.log("Received message from client: " + message, MainInitiator.LOG_LEVEL.INFO);
        mainInitiator.frontendMessageReceived(message, session);
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        mainInitiator.getServer().removeSession(session);
        timerTask.cancel();
        this.timerTask = null;
        System.out.println("Client closed: " + session.getRemoteAddress().getAddress());
    }

    @OnWebSocketError
    public void onError(Session session, Throwable throwable) {
        System.out.println("WebSocket error: " + throwable.getMessage());
    }
}
