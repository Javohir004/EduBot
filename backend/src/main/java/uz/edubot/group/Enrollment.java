package uz.edubot.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import uz.edubot.tenancy.TenantScopedEntity;

/**
 * A student's membership in a group for a period. Leaving a group ends the row instead of
 * deleting it, so attendance and billing history keep their context.
 */
@Entity
@Table(name = "enrollment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment extends TenantScopedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID studentId;

    @Column(nullable = false, updatable = false)
    private UUID groupId;

    @Column(nullable = false)
    private LocalDate joinedOn;

    private LocalDate leftOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    /** Individual monthly fee; {@code null} means the group's fee applies. */
    @Column(precision = 15, scale = 2)
    private BigDecimal feeOverride;

    private String endReason;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    Enrollment(UUID studentId, UUID groupId, LocalDate joinedOn, BigDecimal feeOverride) {
        this.studentId = studentId;
        this.groupId = groupId;
        this.joinedOn = joinedOn;
        this.feeOverride = feeOverride;
    }

    void end(LocalDate on, String reason) {
        this.status = EnrollmentStatus.ENDED;
        this.leftOn = on.isBefore(joinedOn) ? joinedOn : on;
        this.endReason = reason;
    }

    void changeFee(BigDecimal feeOverride) {
        this.feeOverride = feeOverride;
    }
}
