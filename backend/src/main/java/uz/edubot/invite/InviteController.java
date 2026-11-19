package uz.edubot.invite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.center.MembershipRole;
import uz.edubot.common.phone.PhoneNumbers;
import uz.edubot.security.CurrentMember;

@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('INVITE_ADMIN', 'INVITE_STAFF')")
class InviteController {

    private final InviteService inviteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InviteResponse create(@AuthenticationPrincipal CurrentMember member, @Valid @RequestBody CreateInviteRequest request) {
        CreatedInvite created = inviteService.createByMember(member, request.role(), request.phone());
        return InviteResponse.of(created.invite(), created.link());
    }

    @GetMapping
    List<InviteResponse> pending(@AuthenticationPrincipal CurrentMember member) {
        return inviteService.listPending(member.requireCenterId()).stream()
                .map(i -> InviteResponse.of(i, null))
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID id) {
        inviteService.revoke(member.requireCenterId(), id);
    }

    record CreateInviteRequest(@NotNull MembershipRole role, @NotBlank @Size(max = 32) String phone) {
    }

    /**
     * @param link present only in the response that created the invite
     */
    record InviteResponse(UUID id, MembershipRole role, String phone, Instant expiresAt, String link) {

        static InviteResponse of(Invite invite, String link) {
            return new InviteResponse(invite.getId(), invite.getRole(), PhoneNumbers.mask(invite.getExpectedPhone()),
                    invite.getExpiresAt(), link);
        }
    }
}
