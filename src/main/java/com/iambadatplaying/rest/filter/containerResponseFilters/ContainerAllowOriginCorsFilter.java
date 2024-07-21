package com.iambadatplaying.rest.filter.containerResponseFilters;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

import static com.iambadatplaying.rest.filter.containerRequestFilters.ContainerRequestOriginFilter.PROPERTY_ALLOWED_REQUEST;

@Provider
public class ContainerAllowOriginCorsFilter implements ContainerResponseFilter {

    /*
    * Very careful here! Applying this without the ContainerRequestOriginFilter will allow any origin to access the endpoints.
    * We just want to add the header "Access-Control-Allow-Origin" to the response context as we already aborted the
    * request in the ContainerRequestOriginFilter if the origin was not allowed. We
    *
    *
    * */

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Object allowed = requestContext.getProperty(PROPERTY_ALLOWED_REQUEST);

        if (allowed == null || !(boolean) allowed) {
            return;
        }

        String origin = requestContext.getHeaders().getFirst("Origin");

        if (origin == null) {
            origin = "*";
        }

        responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
    }
}