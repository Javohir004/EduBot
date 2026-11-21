package uz.edubot.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(3, 5);

    @Test
    void blocksAuthAfterTheLimitPerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("/api/v1/auth/telegram", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("/api/v1/auth/telegram", "10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");

        // Another client and the regular API have their own buckets.
        assertThat(call("/api/v1/auth/telegram", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(call("/api/v1/me", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresNonApiPaths() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("/actuator/health", "10.0.0.3").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse call(String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
