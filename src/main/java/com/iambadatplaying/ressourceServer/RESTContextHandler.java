package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.MainInitiator;
import com.iambadatplaying.restServlets.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class RESTContextHandler extends ServletContextHandler {

    private MainInitiator mainInitiator;

    public RESTContextHandler(MainInitiator mainInitiator) {
        super(SESSIONS);
        this.mainInitiator = mainInitiator;
        setContextPath("/rest");
        addAllServlets();
    }

    private void addAllServlets() {
        getServletContext().setAttribute("mainInitiator", mainInitiator);

        ServletHolder statusServletHolder = new ServletHolder(StatusServlet.class);
        addServlet(statusServletHolder, "/status");

        ServletHolder taskManagerStatusServletHolder = new ServletHolder(TaskManagerStatusServlet.class);
        addServlet(taskManagerStatusServletHolder, "/taskManager/status");

        ServletHolder taskManagerStartServletHolder = new ServletHolder(TaskHandlerServlet.class);
        addServlet(taskManagerStartServletHolder, "/tasks/*");

        ServletHolder champSelectServletHolder = new ServletHolder(ChampSelectServlet.class);
        addServlet(champSelectServletHolder, "/champSelect/*");

        ServletHolder runesSaveServletHolder = new ServletHolder(RunesSaveServlet.class);
        addServlet(runesSaveServletHolder, "/runes/save");

        ServletHolder shutdownServletHolder = new ServletHolder(ShutdownServlet.class);
        addServlet(shutdownServletHolder, "/shutdown");
    }
}
