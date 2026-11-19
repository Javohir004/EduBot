package uz.edubot.subscription;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.tenancy.TenantContext;

@RestController
@RequiredArgsConstructor
class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/api/v1/subscription")
    @PreAuthorize("hasAuthority('CENTER_READ')")
    SubscriptionResponse current() {
        UUID centerId = TenantContext.require();
        SubscriptionState state = subscriptionService.state(centerId);
        return new SubscriptionResponse(state.planCode(), state.planName(), state.phase().name(), state.endsAt(),
                state.graceUntil(), subscriptionService.usage(centerId), subscriptionService.limits(centerId));
    }

    record SubscriptionResponse(String planCode, String planName, String phase, Instant endsAt, Instant graceUntil,
                                Map<String, Object> usage, Map<String, Long> limits) {
    }
}
