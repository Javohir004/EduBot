package uz.edubot.platform;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.common.web.PageResponse;
import uz.edubot.platform.PlatformDtos.CenterSummary;
import uz.edubot.platform.PlatformDtos.ChangeStatusRequest;
import uz.edubot.platform.PlatformDtos.CreateCenterRequest;
import uz.edubot.platform.PlatformDtos.CreatedCenterResponse;
import uz.edubot.platform.PlatformDtos.PlanResponse;
import uz.edubot.security.CurrentMember;
import uz.edubot.subscription.SubscriptionService;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_MANAGE')")
class PlatformController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlatformService platformService;
    private final SubscriptionService subscriptionService;

    @PostMapping("/centers")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedCenterResponse createCenter(@AuthenticationPrincipal CurrentMember admin,
                                       @Valid @RequestBody CreateCenterRequest request) {
        return platformService.createCenter(request, admin.userId());
    }

    @GetMapping("/centers")
    PageResponse<CenterSummary> centers(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return platformService.listCenters(pageable);
    }

    @PatchMapping("/centers/{id}/status")
    CenterSummary changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return platformService.changeStatus(id, request.status());
    }

    @GetMapping("/plans")
    List<PlanResponse> plans() {
        return subscriptionService.activePlans().stream().map(PlanResponse::of).toList();
    }
}
