package com.iambadatplaying.rest.filter.containerRequestFilters;

import com.iambadatplaying.Starter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ContainerRequestOriginFilter implements ContainerRequestFilter {

    public static final String PROPERTY_ALLOWED_REQUEST = "pc-allowed";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String origin = requestContext.getHeaders().getFirst("Origin");
        if (Starter.getInstance().getResourceServer().filterOrigin(origin)) {
            requestContext.setProperty(PROPERTY_ALLOWED_REQUEST, false);
            requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity("Origin not allowed")
                            .build()
            );
        } else {
            requestContext.setProperty(PROPERTY_ALLOWED_REQUEST, true);
        }
    }
}
