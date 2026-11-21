package uz.edubot.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP rate limits: a strict one for authentication (initData exchange) and a generous
 * one for the whole API. Buckets live in memory (single instance in the MVP) in a bounded cache,
 * so an attacker rotating IPs cannot exhaust memory.
 *
 * <p>Behind a reverse proxy set {@code server.forward-headers-strategy=native} so that
 * {@code getRemoteAddr()} is the real client address.
 */
class RateLimitFilter extends OncePerRequestFilter {

    private final int authPerMinute;
    private final int apiPerMinute;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    RateLimitFilter(int authPerMinute, int apiPerMinute) {
        this.authPerMinute = authPerMinute;
        this.apiPerMinute = apiPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = request.getRequestURI().startsWith("/api/v1/auth/");
        String key = (auth ? "auth:" : "api:") + request.getRemoteAddr();
        int limit = auth ? authPerMinute : apiPerMinute;
        Bucket bucket = buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, Duration.ofMinutes(1)).build())
                .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many requests, try again later\",\"code\":\"RATE_LIMITED\"}");
    }
}
