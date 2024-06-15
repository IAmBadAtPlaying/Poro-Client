package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.ConfigLoader;
import com.iambadatplaying.Starter;
import com.iambadatplaying.config.ConfigModule;
import com.iambadatplaying.rest.filter.InitializerFilter;
import com.iambadatplaying.rest.filter.OptionsCorsFilter;
import com.iambadatplaying.rest.filter.OriginFilter;
import com.iambadatplaying.rest.servlets.*;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

public class RESTContextHandler extends ServletContextHandler {

    private final Starter starter;

    public RESTContextHandler(Starter starter) {
        super(SESSIONS);
        this.starter = starter;
        setContextPath("/rest");
        addAllServlets();
    }

    private static String buildConfigProviderList(ConfigModule configModule) {
        StringBuilder sb = new StringBuilder();
        sb.append(configModule.getRestServlet());
        sb.append(",");

        for (Class c : configModule.getServletConfiguration()) {
            if (c == null) continue;
            sb.append(c.getCanonicalName());
            sb.append(",");
        }

        // Remove trailing comma
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private static String buildStatusConfig() {
        StringBuilder sb = new StringBuilder();

        buildGenericList(
                sb,
                OriginFilter.class,
                OptionsCorsFilter.class
        );

        buildProviderList(
                sb,
                com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyReader.class,
                com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyWriter.class
        );

        buildServletList(
                sb,
                com.iambadatplaying.rest.jerseyServlets.StatusServlet.class
        );

        // Remove trailing comma
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private static String buildProtectedRestConfig() {
        StringBuilder sb = new StringBuilder();

        buildGenericList(
                sb,
                MultiPartFeature.class
        );

        buildProviderList(
                sb,
                com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyReader.class,
                com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyWriter.class
        );

        buildServletList(
                sb,
                com.iambadatplaying.rest.jerseyServlets.TaskHandlerServlet.class,
                com.iambadatplaying.rest.jerseyServlets.LCDSProxyServlet.class,
                com.iambadatplaying.rest.jerseyServlets.LootServlet.class,
                com.iambadatplaying.rest.jerseyServlets.MessagingServlet.class,
                com.iambadatplaying.rest.jerseyServlets.ShutdownServlet.class,
                com.iambadatplaying.rest.jerseyServlets.RunesServlet.class
        );

        // Remove trailing comma
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private static void buildGenericList(StringBuilder sb, Class... classes) {
        for (Class c : classes) {
            if (c == null) continue;
            sb.append(c.getCanonicalName());
            sb.append(",");
        }
    }

    @SafeVarargs
    private static void buildServletList(StringBuilder sb, Class... classes) {
        for (Class c : classes) {
            if (c == null) continue;
            if (!c.isAnnotationPresent(javax.ws.rs.Path.class)) {
                throw new IllegalArgumentException("Class " + c.getCanonicalName() + " is not annotated with @Path");
            }
            sb.append(c.getCanonicalName());
            sb.append(",");
        }
    }

    private static void buildProviderList(StringBuilder sb, Class... classes) {
        for (Class c : classes) {
            if (c == null) continue;
            if (!c.isAnnotationPresent(javax.ws.rs.ext.Provider.class)) {
                throw new IllegalArgumentException("Class " + c.getCanonicalName() + " is not annotated with @Provider");
            }
            sb.append(c.getCanonicalName());
            sb.append(",");
        }
    }

    private void addAllServlets() {
        getServletContext().setAttribute("mainInitiator", starter);

        ServletHolder statusServletHolder = addServlet(ServletContainer.class, "/*");
        statusServletHolder.setInitOrder(0);
        statusServletHolder.setInitParameter(
                "jersey.config.server.provider.classnames",
                buildStatusConfig()
        );


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

        ServletHolder lcdsProxyServletHolder = new ServletHolder(LCDSProxyServlet.class);
        addServlet(lcdsProxyServletHolder, "/lcds");

        ServletHolder conversationServletHolder = new ServletHolder(MessagingServlet.class);
        addServlet(conversationServletHolder, "/conversations/*");

        ServletHolder test = new ServletHolder(Userconfig.class);
        addServlet(test, "/userconfig/*");

        ServletHolder lootServletHolder = new ServletHolder(LootServlet.class);
        addServlet(lootServletHolder, "/loot/*");

        ServletHolder uploadTest = new ServletHolder(UploadServlet.class);
        addServlet(uploadTest, "/dynamic/*");

        FilterHolder initFilterHolder = new FilterHolder(InitializerFilter.class);
        FilterHolder originFilterHolder = new FilterHolder(OriginFilter.class);

        addFilter(initFilterHolder, "/v2/*", EnumSet.of(DispatcherType.REQUEST));
        addFilter(originFilterHolder, "/v2/*", EnumSet.of(DispatcherType.REQUEST));

        ServletHolder jerseyServlet = addServlet(ServletContainer.class, "/v2/*");
        jerseyServlet.setInitOrder(0);
        jerseyServlet.setInitParameter(
                "jersey.config.server.provider.classnames",
                buildProtectedRestConfig()
        );

        addConfigServlets();
    }

    private void addConfigServlets() {
        ConfigLoader configLoader = starter.getConfigLoader();
        ConfigModule[] configModules = configLoader.getConfigModules();
        for (ConfigModule configModule : configModules) {
            String contextPath = "/config/" + configModule.getRestPath() + "/*";
            log("Adding config Module: " + configModule.getClass().getSimpleName() + ", available at: " + contextPath, Starter.LOG_LEVEL.INFO);
            ServletHolder servletHolder = addServlet(ServletContainer.class, contextPath);
            servletHolder.setInitOrder(0);
            servletHolder.setInitParameter(
                    "jersey.config.server.provider.classnames",
                    buildConfigProviderList(configModule)
            );
        }
    }

    private void log(String message) {
        log(message, Starter.LOG_LEVEL.DEBUG);
    }

    private void log(String message, Starter.LOG_LEVEL level) {
        Starter.getInstance().log(this.getClass().getSimpleName() + ": " + message, level);
    }
}
