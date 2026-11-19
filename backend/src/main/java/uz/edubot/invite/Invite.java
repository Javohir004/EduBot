package uz.edubot.invite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import uz.edubot.center.MembershipRole;

/**
 * Not a {@code TenantScopedEntity}: accepting an invite happens before the user belongs to the
 * center, so every query on this table passes the center id (or the token hash) explicitly.
 */
@Entity
@Table(name = "invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invite {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID centerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MembershipRole role;

    @Column(nullable = false, updatable = false)
    private String expectedPhone;

    @Column(nullable = false, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant usedAt;

    private UUID usedBy;

    private Instant revokedAt;

    /** Teacher or guardian profile linked to the accepting user; null for staff-only invites. */
    @Column(updatable = false)
    private UUID targetId;

    @Column(updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    Invite(UUID centerId, MembershipRole role, String expectedPhone, String tokenHash, Instant expiresAt,
           UUID targetId, UUID createdBy) {
        this.targetId = targetId;
        this.centerId = centerId;
        this.role = role;
        this.expectedPhone = expectedPhone;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    boolean isPending(Instant now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    void markUsed(UUID userId, Instant now) {
        this.usedBy = userId;
        this.usedAt = now;
    }

    void revoke(Instant now) {
        this.revokedAt = now;
    }
}
