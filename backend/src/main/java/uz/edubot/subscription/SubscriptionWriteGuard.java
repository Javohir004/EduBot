package uz.edubot.subscription;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uz.edubot.common.error.ApiException;
import uz.edubot.security.CurrentMember;

/**
 * Read-only mode: after the grace period a center can still view everything (and export later)
 * but cannot change data until the subscription is renewed. Data is never deleted.
 */
@Component
@RequiredArgsConstructor
class SubscriptionWriteGuard implements HandlerInterceptor, WebMvcConfigurer {

    private final SubscriptionService subscriptionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentMember member) || member.centerId() == null) {
            return true;
        }
        if (!subscriptionService.state(member.centerId()).allowsWrites()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_EXPIRED",
                    "The subscription has expired; the center is read-only until it is renewed");
        }
        return true;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**", "/api/v1/platform/**");
    }
}
