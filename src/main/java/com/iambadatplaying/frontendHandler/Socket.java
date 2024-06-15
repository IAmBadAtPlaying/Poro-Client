package com.iambadatplaying.frontendHandler;

import com.google.gson.JsonArray;
import com.iambadatplaying.ConnectionStatemachine;
import com.iambadatplaying.Starter;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

@WebSocket
public class Socket {

    private final Starter starter;

    private TimerTask timerTask;

    private Timer timer = new java.util.Timer();

    private Thread messageSenderThread;

    private Session currentSession = null;

    private volatile boolean shutdownPending = false;

    private ConcurrentLinkedQueue<String> messageQueue;

    public Socket(Starter starter) {
        this.starter = starter;
        this.messageQueue = new ConcurrentLinkedQueue<>();
        log("Socket created", Starter.LOG_LEVEL.DEBUG);
        messageSenderThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (messageQueue == null || messageQueue.isEmpty() || currentSession == null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                    continue;
                }
                String message = messageQueue.poll();
                if (message == null) continue;
                try {
                    currentSession.getRemote().sendString(message);
                } catch (Exception e) {

                }
            }
        });
    }

    public void shutdown() {
        if (!shutdownPending) {
            starter.getServer().removeSocket(this);
            externalShutdown();
        }
        log("Socket shutdown", Starter.LOG_LEVEL.DEBUG);
    }

    public void externalShutdown() {
        shutdownPending = true;
        if (currentSession != null) {
            try {
                currentSession.disconnect();
            } catch (Exception e) {

            }
        }
        this.currentSession = null;
        if (this.timerTask != null) {
            timerTask.cancel();
            this.timerTask = null;
        }
        if (this.messageSenderThread != null) {
            messageSenderThread.interrupt();
            this.messageSenderThread = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (messageQueue != null) {
            messageQueue.clear();
            messageQueue = null;
        }
    }

    public void sendMessage(String message) {
        if (messageQueue != null) messageQueue.offer(message);
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        if (this.currentSession != null) {
            try {
                session.disconnect();
            } catch (Exception e) {

            }
        }
        currentSession = session;
        log("Client connected: " + session.getRemoteAddress().getAddress());
        messageSenderThread.start();
        starter.getServer().addSocket(this);
        queueNewKeepAlive(session);
        starter.getFrontendMessageHandler().sendCurrentState(this);
        if (starter.getConnectionStatemachine().getCurrentState() == ConnectionStatemachine.State.CONNECTED) {
            starter.getFrontendMessageHandler().sendInitialData(this);
        }
    }


    private void queueNewKeepAlive(Session s) {
        this.timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    s.getRemote().sendString(new JsonArray().toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                queueNewKeepAlive(s);
            }
        };
        timer.schedule(timerTask, 290000);
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        if (starter.getConnectionStatemachine().getCurrentState() != ConnectionStatemachine.State.CONNECTED) {
        }
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        shutdown();
    }

    @OnWebSocketError
    public void onError(Session session, Throwable throwable) {
        log("WebSocket error: " + throwable.getMessage(), Starter.LOG_LEVEL.ERROR);
    }

    private void log(String s, Starter.LOG_LEVEL level) {
        starter.log(this.getClass().getSimpleName() + ": " + s, level);
    }

    private void log(String s) {
        log(s, Starter.LOG_LEVEL.DEBUG);
    }
}
