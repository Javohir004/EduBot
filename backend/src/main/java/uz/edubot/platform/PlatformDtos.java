package uz.edubot.platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uz.edubot.center.CenterStatus;
import uz.edubot.center.EducationCenter;
import uz.edubot.subscription.SubscriptionPlan;
import uz.edubot.subscription.SubscriptionStatus;
import uz.edubot.subscription.SubscriptionView;

final class PlatformDtos {

    static final int DEFAULT_TRIAL_DAYS = 14;

    private PlatformDtos() {
    }

    record CreateCenterRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 32) String phone,
            @Size(max = 300) String address,
            @NotBlank @Size(max = 32) String ownerPhone,
            @NotBlank @Size(max = 32) String planCode,
            @Min(1) @Max(90) Integer trialDays) {

        int trialDaysOrDefault() {
            return trialDays == null ? DEFAULT_TRIAL_DAYS : trialDays;
        }
    }

    record ChangeStatusRequest(@NotNull CenterStatus status) {
    }

    record CenterSummary(UUID id, String name, String phone, CenterStatus status, Instant createdAt,
                         String planCode, SubscriptionStatus subscriptionStatus, Instant subscriptionEndsAt) {

        static CenterSummary of(EducationCenter center, SubscriptionView subscription) {
            return new CenterSummary(center.getId(), center.getName(), center.getPhone(), center.getStatus(),
                    center.getCreatedAt(),
                    subscription == null ? null : subscription.planCode(),
                    subscription == null ? null : subscription.status(),
                    subscription == null ? null : subscription.endsAt());
        }
    }

    record OwnerInvite(String link, Instant expiresAt) {
    }

    record CreatedCenterResponse(CenterSummary center, OwnerInvite ownerInvite) {
    }

    record PlanResponse(String code, String name, BigDecimal monthlyPrice, String currency) {

        static PlanResponse of(SubscriptionPlan plan) {
            return new PlanResponse(plan.getCode(), plan.getName(), plan.getMonthlyPrice(), plan.getCurrency());
        }
    }
}
