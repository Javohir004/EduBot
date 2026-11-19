package uz.edubot.platform;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.center.CenterService;
import uz.edubot.center.CenterStatus;
import uz.edubot.center.EducationCenter;
import uz.edubot.center.MembershipRole;
import uz.edubot.common.web.PageResponse;
import uz.edubot.invite.CreatedInvite;
import uz.edubot.invite.InviteService;
import uz.edubot.platform.PlatformDtos.CenterSummary;
import uz.edubot.platform.PlatformDtos.CreateCenterRequest;
import uz.edubot.platform.PlatformDtos.CreatedCenterResponse;
import uz.edubot.platform.PlatformDtos.OwnerInvite;
import uz.edubot.subscription.SubscriptionService;
import uz.edubot.subscription.SubscriptionView;

/**
 * Onboarding is sales-led for the MVP: the platform admin creates the center, starts a trial and
 * sends the owner a one-time invite link.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class PlatformService {

    private final CenterService centerService;
    private final SubscriptionService subscriptionService;
    private final InviteService inviteService;

    @Transactional
    CreatedCenterResponse createCenter(CreateCenterRequest request, UUID platformAdminId) {
        EducationCenter center = centerService.create(request.name(), request.phone(), request.address());
        SubscriptionView subscription = subscriptionService.startTrial(center.getId(), request.planCode(),
                Duration.ofDays(request.trialDaysOrDefault()), platformAdminId);
        CreatedInvite invite = inviteService.create(center.getId(), MembershipRole.OWNER, request.ownerPhone(), null,
                platformAdminId);
        log.info("Center created id={} plan={}", center.getId(), subscription.planCode());
        return new CreatedCenterResponse(CenterSummary.of(center, subscription),
                new OwnerInvite(invite.link(), invite.invite().getExpiresAt()));
    }

    @Transactional(readOnly = true)
    PageResponse<CenterSummary> listCenters(Pageable pageable) {
        Page<EducationCenter> page = centerService.findAll(pageable);
        List<UUID> ids = page.getContent().stream().map(EducationCenter::getId).toList();
        Map<UUID, SubscriptionView> subscriptions = subscriptionService.findCurrent(ids);
        return PageResponse.of(page, c -> CenterSummary.of(c, subscriptions.get(c.getId())));
    }

    @Transactional(readOnly = true)
    void requireCenter(UUID centerId) {
        centerService.findById(centerId)
                .orElseThrow(() -> uz.edubot.common.error.ApiException.notFound("CENTER_NOT_FOUND", "Center not found"));
    }

    @Transactional
    CenterSummary changeStatus(UUID centerId, CenterStatus status) {
        EducationCenter center = centerService.changeStatus(centerId, status);
        return CenterSummary.of(center, subscriptionService.findCurrent(List.of(centerId)).get(centerId));
    }
}
