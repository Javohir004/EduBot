package uz.edubot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless bearer-token API. The SPA is served from the same origin, so no CORS configuration
 * is needed; CSRF protection is unnecessary because no cookies are used for authentication.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailSecurityHandler problemHandler,
                                    MemberAuthenticationConverter memberConverter,
                                    @Value("${edubot.rate-limit.auth-per-minute:20}") int authPerMinute,
                                    @Value("${edubot.rate-limit.api-per-minute:600}") int apiPerMinute) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Telegram Web shows Mini Apps in an iframe: allow only Telegram as the embedding parent.
                .headers(h -> h
                        .frameOptions(f -> f.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "frame-ancestors 'self' https://web.telegram.org https://*.telegram.org")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/telegram").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Mini App files when the backend serves web/dist (local development).
                        .requestMatchers(request -> "GET".equals(request.getMethod())
                                && !request.getRequestURI().startsWith("/api/")
                                && !request.getRequestURI().startsWith("/actuator")).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(memberConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .addFilterBefore(new RateLimitFilter(authPerMinute, apiPerMinute), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
