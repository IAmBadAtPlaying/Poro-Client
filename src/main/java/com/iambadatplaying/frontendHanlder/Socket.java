package com.iambadatplaying.frontendHanlder;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.IOException;

@WebSocket
public class Socket {

    private MainInitiator mainInitiator;

    public Socket(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
        System.out.println("Created Socket!");
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("Client connected: " + session.getRemoteAddress().getAddress());
        mainInitiator.getServer().addSession(session);
        try {
            session.getRemote().sendString("[]");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        System.out.println("Received message from client: " + message);
        mainInitiator.frontendMessageReceived(message, session);
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        System.out.println("Client closed: " + session.getRemoteAddress().getAddress());
        mainInitiator.getServer().removeSession(session);
    }

    @OnWebSocketError
    public void onError(Session session, Throwable throwable) {
        System.out.println("WebSocket error: " + throwable.getMessage());
    }
}
