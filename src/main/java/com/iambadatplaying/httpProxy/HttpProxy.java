package com.iambadatplaying.httpProxy;

import com.iambadatplaying.MainInitiator;
import org.eclipse.jetty.server.Server;

public class HttpProxy {

    private MainInitiator mainInitiator;

    private Server server;

    public HttpProxy(MainInitiator mainInitiator) {
        this.mainInitiator = mainInitiator;
    }

    public void init() {
        server = new Server(2023);
        server.setHandler(new ProxyHandler(mainInitiator));

        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        server = null;
    }
}
