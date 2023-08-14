package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;
import java.util.ArrayList;

public class SocketClient {

    private MainInitiator mainInitiator = null;
    private WebSocketClient client = null;
    private volatile Socket socket = null;

    private int MAXIMUM_TEXT_SIZE = 3000000; //Sometimes really huge messages get send IDK why

    public SocketClient(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void init() {
        ConnectionManager cm = mainInitiator.getConnectionManager();
        if(cm.authString == null) {
            return;
        }
        SslContextFactory ssl = new SslContextFactory.Client(false);

        HttpClient http = new HttpClient(ssl);
        String sUri = "wss://127.0.0.1:"+cm.port+"/";
        this.client = new WebSocketClient(http);
        client.getPolicy().setMaxTextMessageSize(MAXIMUM_TEXT_SIZE);
        client.setMaxTextMessageBufferSize(MAXIMUM_TEXT_SIZE);
        socket = new Socket(mainInitiator);

        ssl.setSslContext(mainInitiator.getConnectionManager().sslContextGlobal);
        try {
            client.start();
            URI uri = new URI(sUri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            ClientUpgradeRequest f = new ClientUpgradeRequest();
            request.setHeader("Authorization",cm.authString);
            client.connect(socket, uri, request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        try {
            client.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        client = null;
        socket = null;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }
}
