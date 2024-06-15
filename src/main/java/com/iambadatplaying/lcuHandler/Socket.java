package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.ConnectionStatemachine;
import com.iambadatplaying.Starter;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.util.TimerTask;

@WebSocket
public class Socket {

    Starter starter;
    private TimerTask timerTask;
    private volatile boolean connected = false;
    private Session currentSession;

    public Socket(Starter starter) {
        this.starter = starter;
    }

    public boolean isConnected() {
        return connected;
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        this.connected = false;
        log("Closed: " + reason, Starter.LOG_LEVEL.DEBUG);
        timerTask.cancel();
        this.timerTask = null;
        starter.getConnectionStatemachine().transition(ConnectionStatemachine.State.DISCONNECTED);
    }

    @OnWebSocketError
    public void onError(Throwable t) {
        if ((t.getMessage() != null) && !t.getMessage().equals("null")) {
            log(t.getMessage(), Starter.LOG_LEVEL.ERROR);
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        log("Connect: " + session.getRemoteAddress().getAddress(), Starter.LOG_LEVEL.INFO);
        this.currentSession = session;
        this.connected = true;
        subscribeToEndpoint("OnJsonApiEvent");
        createNewKeepAlive(session);
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        starter.backendMessageReceived(message);
    }

    private void createNewKeepAlive(Session s) {
        log("Created new Keep alive!", Starter.LOG_LEVEL.DEBUG);
        this.timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    s.getRemote().sendString("");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                createNewKeepAlive(s);
            }
        };
        new java.util.Timer().schedule(timerTask, 290000);
    }

    public void subscribeToEndpoint(String endpoint) {
        try {
            log("Subscribing to: " + endpoint);
            currentSession.getRemote().sendString("[5, \"" + endpoint + "\"]");
        } catch (Exception e) {
            log("Cannot subscribe to endpoint " + endpoint, Starter.LOG_LEVEL.DEBUG);
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                subscribeToEndpoint(endpoint);
            }).start();
        }
    }

    public void unsubscribeFromEndpoint(String endpoint) {
        try {
            log("Unsubscribing from: " + endpoint);
            currentSession.getRemote().sendString("[6, \"" + endpoint + "\"]");
        } catch (Exception e) {
            log("Cannot unsubscribe from endpoint " + endpoint, Starter.LOG_LEVEL.ERROR);
            e.printStackTrace();
        }
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        starter.log(this.getClass().getSimpleName() + ": " + s);
    }
}
