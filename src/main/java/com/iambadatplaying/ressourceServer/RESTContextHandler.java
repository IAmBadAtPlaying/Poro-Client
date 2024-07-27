package com.iambadatplaying.ressourceServer;

import com.iambadatplaying.ConfigLoader;
import com.iambadatplaying.Starter;
import com.iambadatplaying.config.ConfigModule;
import com.iambadatplaying.rest.filter.InitializerFilter;
import com.iambadatplaying.rest.filter.containerRequestFilters.ContainerRequestOriginFilter;
import com.iambadatplaying.rest.filter.containerRequestFilters.ContainerOptionsCorsFilter;
import com.iambadatplaying.rest.filter.OriginFilter;
import com.iambadatplaying.rest.filter.containerResponseFilters.ContainerAllowOriginCorsFilter;
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

    private String buildConfigProviderList(ConfigModule configModule) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> c : configModule.getServletConfiguration()) {
            if (c == null) continue;
            sb.append(c.getCanonicalName());
            sb.append(",");
        }
        sb.append(configModule.getRestServlet().getCanonicalName());
        return sb.toString();
    }

    private static String buildStaticConfig() {
        StringBuilder sb = new StringBuilder();

        buildGenericList(
                sb,
                ContainerRequestOriginFilter.class,
                ContainerOptionsCorsFilter.class,

                ContainerAllowOriginCorsFilter.class
        );

        buildProviderList(
                sb,
                com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyReader.class,
                com.iambadatplaying.rest.providers.GsonJsonElementMessageBodyWriter.class
        );

        buildServletList(
                sb,
                StatusServlet.class,
                ShutdownServlet.class
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
                TaskHandlerServlet.class,
                LCDSProxyServlet.class,
                LootServlet.class,
                MessagingServlet.class,
                RunesServlet.class,
                ChampSelectServlet.class
        );

        // Remove trailing comma
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private static void buildGenericList(StringBuilder sb, Class<?>... classes) {
        for (Class<?> c : classes) {
            if (c == null) continue;
            sb.append(c.getCanonicalName());
            sb.append(",");
        }
    }

    private static void buildServletList(StringBuilder sb, Class<?>... classes) {
        for (Class<?> c : classes) {
            if (c == null) continue;
            if (!c.isAnnotationPresent(javax.ws.rs.Path.class)) {
                throw new IllegalArgumentException("Class " + c.getCanonicalName() + " is not annotated with @Path");
            }
            sb.append(c.getCanonicalName());
            sb.append(",");
        }
    }

    private static void buildProviderList(StringBuilder sb, Class<?>... classes) {
        for (Class<?> c : classes) {
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
                buildStaticConfig()
        );


        FilterHolder initFilterHolder = new FilterHolder(InitializerFilter.class);
        FilterHolder originFilterHolder = new FilterHolder(OriginFilter.class);
        addFilter(initFilterHolder, "/v1/*", EnumSet.of(DispatcherType.REQUEST));
        addFilter(originFilterHolder, "/v1/*", EnumSet.of(DispatcherType.REQUEST));

        ServletHolder jerseyServlet = addServlet(ServletContainer.class, "/v1/*");
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
            log("Successfully added config Module: " + configModule.getClass().getSimpleName(), Starter.LOG_LEVEL.INFO);
        }
    }

    private void log(String message) {
        log(message, Starter.LOG_LEVEL.DEBUG);
    }

    private void log(String message, Starter.LOG_LEVEL level) {
        Starter.getInstance().log(this.getClass().getSimpleName() + ": " + message, level);
    }
}
