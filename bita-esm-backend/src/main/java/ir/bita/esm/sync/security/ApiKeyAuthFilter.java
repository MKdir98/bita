package ir.bita.esm.sync.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API key authentication filter for internal endpoints.
 */
@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    @Value("${app.internal.api-key:internal-api-key-change-in-production}")
    private String configuredApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only apply to internal endpoints
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Missing API key for internal endpoint: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Missing API key\", \"message\": \"X-API-Key header is required\"}");
            return;
        }

        if (!configuredApiKey.equals(apiKey)) {
            log.warn("Invalid API key for internal endpoint: {}", path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid API key\", \"message\": \"The provided API key is invalid\"}");
            return;
        }

        log.debug("API key validated for internal endpoint: {}", path);
        filterChain.doFilter(request, response);
    }
}
