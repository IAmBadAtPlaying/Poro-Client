package com.iambadatplaying.config;

import com.google.gson.JsonElement;

public interface ConfigModule {

    /**
     * This should load the configuration from the given JsonElement and set the configuration of the module
     */
    boolean loadConfiguration();
    boolean loadConfiguration(JsonElement config);

    boolean saveConfiguration();

    /**
     * This should load the standard configuration of the module that gets loaded in case the configuration for the KEY is not present
     */
    boolean loadStandardConfiguration();

    boolean setupDirectories();

    /**
     * @return The configuration of the module
     */
    JsonElement getConfiguration();

    /**
     * @return The path to the REST endpoint
     */
    String getRestPath();

    /**
     * @return The configuration of the servlet (i.e. Filters, etc.) but not the servlet itself
     */
    Class<?>[] getServletConfiguration();

    Class<? extends ConfigServlet> getRestServlet();
}
