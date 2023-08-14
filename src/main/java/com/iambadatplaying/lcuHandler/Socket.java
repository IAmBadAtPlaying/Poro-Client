package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.TimerTask;

@WebSocket
public class Socket {

    MainInitiator mainInitiator;

    public Socket(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
        this.subscribeQueue = new PriorityQueue<>();
    }

    public volatile TimerTask timerTask;

    private Queue<String> subscribeQueue;

    private volatile boolean connected = false;

    private Session currentSession;

    public boolean isConnected() {
        return connected;
    }

    private void emptySubscribeQueue() {
        System.out.println("Emptying subscribe queue");
        while (connected) {
            try {
                String subscribeMessage = subscribeQueue.poll();
                if (subscribeMessage != null) {
                    sendSubscribeMessage(subscribeMessage);
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        this.connected = false;
        log("Closed: " + reason, MainInitiator.LOG_LEVEL.DEBUG);
        timerTask.cancel();
        this.timerTask = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                mainInitiator.handleGracefulReset();
            }
        }).start();
    }

    @OnWebSocketError
    public void onError(Throwable t) {
        if (!(t.getMessage() == null) && !t.getMessage().equals("null")) {
            log(t.getMessage(), MainInitiator.LOG_LEVEL.ERROR);
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        log("Connect: " + session.getRemoteAddress().getAddress(), MainInitiator.LOG_LEVEL.INFO);
        this.currentSession = session;
        this.connected = true;
        createNewKeepAlive(session);
        new Thread(this::emptySubscribeQueue).start();
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        if (!(message == null) && !message.isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mainInitiator.getBackendMessageHandler().handleMessage(message);
                }
            }).start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mainInitiator.getTaskManager().updateAllTasks(message);
                }
            }).start();
        }
    }

    public void createNewKeepAlive(Session s) {
        log("Created new Keep alive!", MainInitiator.LOG_LEVEL.DEBUG);
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

    public Session getCurrentSession() {
        return currentSession;
    }

    private void sendSubscribeMessage(String endpoint) {
        try {
            log( "Subscribing to: " +endpoint);
            currentSession.getRemote().sendString("[5, \""+endpoint+"\"]");
        } catch (Exception e) {
            log("Cannot subscribe to endpoint " +endpoint, MainInitiator.LOG_LEVEL.ERROR);
            e.printStackTrace();
        }
    }

    public void subscribeToEndpoint(String endpoint) {
        subscribeQueue.add(endpoint);
    }

    public void unsubscribeFromEndpoint(String endpoint) {
        try {
            log( "Unsubscribing from: " +endpoint);
            currentSession.getRemote().sendString("[6, \""+endpoint+"\"]");
        } catch (Exception e) {
            log( "Cannot unsubscribe from endpoint " +endpoint, MainInitiator.LOG_LEVEL.ERROR);
            e.printStackTrace();
        }
    }

    private void log(String s, MainInitiator.LOG_LEVEL level) {
        mainInitiator.log(this.getClass().getName() +": " + s, level);
    }

    private void log(String s) {
        mainInitiator.log(this.getClass().getName() +": " +s);
    }
}
