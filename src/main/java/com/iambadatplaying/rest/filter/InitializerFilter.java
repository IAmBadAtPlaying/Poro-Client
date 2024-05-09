package com.iambadatplaying.rest.filter;

import com.iambadatplaying.Starter;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class InitializerFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        if (!Starter.getInstance().isInitialized()) {
            ((HttpServletResponse) servletResponse).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service is not initialized");
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
