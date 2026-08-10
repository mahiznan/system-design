package com.systemdesign.ratelimiter.gateway;

import com.systemdesign.ratelimiter.ApiKeyRegistry;
import com.systemdesign.ratelimiter.Tier;
import com.systemdesign.ratelimiter.ratelimit.Decision;
import com.systemdesign.ratelimiter.ratelimit.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;

/**
 * Standalone gateway-style proxy (PROBLEM.md FR5): checks the caller's rate
 * limit, then either rejects with 429 or forwards to the echo backend and
 * relays its response, adding the X-RateLimit-* headers either way (FR3/FR4).
 */
@RestController
public class GatewayController {

    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of("host", "content-length", "connection");

    private final ApiKeyRegistry apiKeyRegistry;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient;

    public GatewayController(ApiKeyRegistry apiKeyRegistry,
                              RateLimiter rateLimiter,
                              MeterRegistry meterRegistry,
                              @Value("${ratelimiter.backend-base-url}") String backendBaseUrl) {
        this.apiKeyRegistry = apiKeyRegistry;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        this.restClient = RestClient.builder().baseUrl(backendBaseUrl).build();
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> handle(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing X-API-Key header".getBytes(StandardCharsets.UTF_8));
        }

        Optional<Tier> tierOpt = apiKeyRegistry.tierFor(apiKey);
        if (tierOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unknown API key".getBytes(StandardCharsets.UTF_8));
        }
        Tier tier = tierOpt.get();

        Timer.Sample sample = Timer.start(meterRegistry);
        Decision decision = rateLimiter.checkLimit(apiKey, tier);
        sample.stop(meterRegistry.timer("ratelimiter.decision.latency", "tier", tier.name()));
        meterRegistry.counter("ratelimiter.requests",
                "result", decision.allowed() ? "allowed" : "rejected",
                "tier", tier.name(),
                "key", apiKey
        ).increment();

        HttpHeaders rateLimitHeaders = new HttpHeaders();
        rateLimitHeaders.add("X-RateLimit-Limit", String.valueOf(decision.limit()));
        rateLimitHeaders.add("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        rateLimitHeaders.add("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));

        if (!decision.allowed()) {
            rateLimitHeaders.add("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(rateLimitHeaders).build();
        }

        ResponseEntity<byte[]> backendResponse = forward(request, body);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(backendResponse.getHeaders());
        responseHeaders.addAll(rateLimitHeaders);

        return ResponseEntity.status(backendResponse.getStatusCode()).headers(responseHeaders).body(backendResponse.getBody());
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body) {
        String uri = request.getRequestURI()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        RestClient.RequestBodySpec forwardRequest = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(uri);

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (SKIP_REQUEST_HEADERS.contains(headerName.toLowerCase())) {
                continue;
            }
            forwardRequest.header(headerName, Collections.list(request.getHeaders(headerName)).toArray(new String[0]));
        }

        return forwardRequest
                .body(body == null ? new byte[0] : body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { /* pass errors through, don't throw */ })
                .toEntity(byte[].class);
    }
}
