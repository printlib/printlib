package qz.ws;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

/**
 * CORS filter to handle cross-origin requests for WebSocket connections.
 * 
 * This filter adds the necessary CORS headers to allow cross-origin WebSocket
 * connections from web applications running on different domains.
 */
@Log4j2
public class CorsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("CORS Filter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String origin = httpRequest.getHeader("Origin");
        log.debug("Processing request with Origin: {}", origin);
        
        // Allow all origins for WebSocket connections
        // This is necessary because CORS doesn't apply to WebSocket upgrade requests
        httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        httpResponse.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
        httpResponse.setHeader("Access-Control-Allow-Credentials", "false");
        httpResponse.setHeader("Access-Control-Max-Age", "3600");
        
        // Handle preflight requests
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            log.debug("Handling OPTIONS preflight request from origin: {}", origin);
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        log.debug("CORS headers added for origin: {}", origin);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        log.info("CORS Filter destroyed");
    }
}