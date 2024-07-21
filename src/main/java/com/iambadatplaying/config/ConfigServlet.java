package com.iambadatplaying.config;

import com.google.gson.JsonElement;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.core.Response;

public interface ConfigServlet {
    @GET
    Response getConfig();

    @POST
    Response setConfig(JsonElement data);
}
