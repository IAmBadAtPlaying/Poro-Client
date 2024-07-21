package com.iambadatplaying.rest.filter.containerRequestFilters;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ContainerOptionsCorsFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (requestContext.getMethod().equals("OPTIONS")) {
            requestContext.abortWith(Response.status(Response.Status.OK)
                    .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                    .header("Access-Control-Allow-Origin", "*")
                    .build());
        }
    }
}