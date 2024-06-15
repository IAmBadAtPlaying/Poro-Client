package com.iambadatplaying.lcuHandler;

import com.iambadatplaying.Starter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;

public class SocketClient {

    //Sometimes really huge messages get send by the client.
    //This is caused by the loot, inventory and the friend list.
    //On an alternate account these limits are not reached, on the main account with a lot of skins and friends they are.
    //TODO: This is a workaround, find a better solution as this is not guaranteed to work for everyone
    private static final int MAXIMUM_TEXT_SIZE = 1_024 * 1024 * 10;
    private Starter starter = null;
    private WebSocketClient client = null;
    private Socket socket = null;

    public SocketClient(Starter starter) {
        this.starter = starter;
    }

    public void init() {
        ConnectionManager cm = starter.getConnectionManager();
        if (cm.getAuthString() == null) {
            return;
        }
        SslContextFactory ssl = new SslContextFactory.Client(false);

        HttpClient http = new HttpClient(ssl);
        String sUri = "wss://127.0.0.1:" + cm.getPort() + "/";
        this.client = new WebSocketClient(http);
        client.setStopAtShutdown(true);
        client.getPolicy().setMaxTextMessageSize(MAXIMUM_TEXT_SIZE);
        socket = new Socket(starter);

        ssl.setSslContext(starter.getConnectionManager().getSslContextGlobal());
        try {
            client.start();
            URI uri = new URI(sUri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Authorization", cm.getAuthString());
            client.connect(socket, uri, request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        try {
            if (client != null) client.destroy();
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
