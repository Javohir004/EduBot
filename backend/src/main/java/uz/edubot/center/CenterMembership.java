package uz.edubot.center;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Links a user to a center with one role. Plain id references, no JPA associations:
 * memberships are always loaded through explicit queries.
 */
@Entity
@Table(name = "center_membership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CenterMembership {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID centerId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public CenterMembership(UUID centerId, UUID userId, MembershipRole role) {
        this.centerId = centerId;
        this.userId = userId;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
    }

    void revoke() {
        this.status = MembershipStatus.REVOKED;
    }

    void activate() {
        this.status = MembershipStatus.ACTIVE;
    }
}
