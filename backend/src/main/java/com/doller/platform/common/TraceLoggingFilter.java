package com.doller.platform.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceLoggingFilter extends OncePerRequestFilter {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(TraceLoggingFilter.class);

    private final boolean tradingDebug;

    public TraceLoggingFilter(@Value("${app.logging.trading-debug:false}") boolean tradingDebug) {
        this.tradingDebug = tradingDebug;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = Optional.ofNullable(request.getHeader(TRACE_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        long startedAt = System.currentTimeMillis();
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        if (tradingDebug && isTradingPath(request.getRequestURI())) {
            log.info("http_request_start method={} path={} query={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    sanitizeQuery(request.getQueryString()));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("http_request_complete method={} path={} query={} status={} durationMs={} actor={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    sanitizeQuery(request.getQueryString()),
                    response.getStatus(),
                    durationMs,
                    currentActor());
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private boolean isTradingPath(String path) {
        return path.startsWith("/deals")
                || path.startsWith("/dues")
                || path.startsWith("/ledgers/party")
                || path.startsWith("/dashboard")
                || path.startsWith("/settlements");
    }

    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "-";
        }
        return query
                .replaceAll("(?i)(accessToken|refreshToken|token|password)=[^&]+", "$1=***");
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
