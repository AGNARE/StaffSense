package com.staffsense.demo.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> ipBucketCache;

    public RateLimitingFilter() {
        this.ipBucketCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Only intercept POST /login requests
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !"/login".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        Bucket bucket = ipBucketCache.get(clientIp, k -> createNewBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            // Refill rate is 1 token per 60 seconds; Retry-After is always 60 when rate limited
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");

            // For form POSTs (browser login), redirect to login page with error param
            String acceptHeader = request.getHeader("Accept");
            if (acceptHeader != null && acceptHeader.contains("text/html")) {
                response.sendRedirect("/login?error=rate_limit");
            } else {
                // For API clients, return JSON error
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        "{\"error\":\"Слишком много попыток. Пожалуйста, попробуйте позже.\"}");
            }
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the first IP in the chain
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(1, Duration.ofSeconds(60)));
        return Bucket.builder().addLimit(limit).build();
    }
}
