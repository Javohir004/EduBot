package uz.edubot.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.audit.AuditService;
import uz.edubot.center.CenterService;
import uz.edubot.center.CenterSettings;
import uz.edubot.center.MembershipRole;
import uz.edubot.center.MembershipService;
import uz.edubot.center.MembershipService.StaffMember;
import uz.edubot.security.CurrentMember;
import uz.edubot.tenancy.TenantContext;

/** Center settings and staff management (owner only for changes). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class SettingsController {

    private final CenterService centerService;
    private final MembershipService membershipService;
    private final AuditService audit;

    @GetMapping("/center/settings")
    @PreAuthorize("hasAuthority('CENTER_READ')")
    SettingsDto settings() {
        return SettingsDto.of(centerService.settings(TenantContext.require()));
    }

    @PutMapping("/center/settings")
    @PreAuthorize("hasAuthority('CENTER_MANAGE')")
    SettingsDto update(@Valid @RequestBody SettingsDto request) {
        return SettingsDto.of(centerService.updateSettings(TenantContext.require(), request.paymentDueDay(),
                request.absenceNotificationsEnabled(), request.paymentRemindersEnabled()));
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    List<StaffMember> staff() {
        return membershipService.staff(TenantContext.require());
    }

    @DeleteMapping("/staff/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    void revoke(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID membershipId) {
        UUID centerId = TenantContext.require();
        MembershipRole role = membershipService.revoke(centerId, membershipId, member.userId());
        audit.record(centerId, member.userId(), "MEMBERSHIP_REVOKED", "MEMBERSHIP", membershipId,
                Map.of("role", role.name()));
    }

    record SettingsDto(@Min(1) @Max(28) int paymentDueDay, boolean absenceNotificationsEnabled,
                       boolean paymentRemindersEnabled) {

        static SettingsDto of(CenterSettings s) {
            return new SettingsDto(s.getPaymentDueDay(), s.isAbsenceNotificationsEnabled(), s.isPaymentRemindersEnabled());
        }
    }
}
