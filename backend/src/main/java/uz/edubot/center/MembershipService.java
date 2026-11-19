package uz.edubot.center;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final CenterMembershipRepository repository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public record StaffMember(UUID membershipId, UUID userId, String firstName, String lastName, String username,
                              MembershipRole role) {
    }

    /** Active staff memberships (owners, admins, teachers) of the center. */
    @Transactional(readOnly = true)
    public List<StaffMember> staff(UUID centerId) {
        return jdbc.query("""
                        select m.id, u.id, u.first_name, u.last_name, u.username, m.role
                        from center_membership m join app_user u on u.id = m.user_id
                        where m.center_id = ? and m.status = 'ACTIVE' and m.role in ('OWNER', 'ADMIN', 'TEACHER')
                        order by case m.role when 'OWNER' then 0 when 'ADMIN' then 1 else 2 end, u.first_name
                        """, (rs, i) -> new StaffMember(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), MembershipRole.valueOf(rs.getString(6))),
                centerId);
    }

    /**
     * Revokes access; the person's next request fails because roles are checked on every request.
     * Nobody can revoke themselves and the last owner cannot be removed.
     */
    @Transactional
    public MembershipRole revoke(UUID centerId, UUID membershipId, UUID actorUserId) {
        CenterMembership membership = repository.findById(membershipId)
                .filter(m -> m.getCenterId().equals(centerId) && m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> uz.edubot.common.error.ApiException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        if (membership.getUserId().equals(actorUserId)) {
            throw new uz.edubot.common.error.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    "CANNOT_REVOKE_SELF", "You cannot remove yourself");
        }
        if (membership.getRole() == MembershipRole.OWNER) {
            Integer owners = jdbc.queryForObject("select count(*) from center_membership where center_id = ? "
                    + "and role = 'OWNER' and status = 'ACTIVE'", Integer.class, centerId);
            if (owners == null || owners <= 1) {
                throw new uz.edubot.common.error.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                        "LAST_OWNER", "The last owner cannot be removed");
            }
        }
        membership.revoke();
        return membership.getRole();
    }

    /**
     * Grants the role in the center; re-activates a previously revoked membership. Idempotent.
     */
    @Transactional
    public CenterMembership grant(UUID centerId, UUID userId, MembershipRole role) {
        return repository.findByCenterIdAndUserIdAndRole(centerId, userId, role)
                .map(existing -> {
                    existing.activate();
                    return existing;
                })
                .orElseGet(() -> repository.save(new CenterMembership(centerId, userId, role)));
    }

    /**
     * Active memberships of the user in active centers only.
     */
    @Transactional(readOnly = true)
    public List<MembershipView> findActiveForUser(UUID userId) {
        return repository.findActiveViewsByUserId(userId);
    }

    /**
     * Returns the membership only if it belongs to the given user and is currently active.
     */
    @Transactional(readOnly = true)
    public Optional<MembershipView> findActiveForUser(UUID userId, UUID membershipId) {
        return findActiveForUser(userId).stream()
                .filter(m -> m.membershipId().equals(membershipId))
                .findFirst();
    }
}
