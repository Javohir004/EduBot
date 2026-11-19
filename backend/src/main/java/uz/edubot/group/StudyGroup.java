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

@Entity
@Table(name = "study_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyGroup extends TenantScopedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID teacherId;

    @Column(nullable = false)
    private String name;

    private String subject;

    /** Default monthly fee; an enrollment may override it (discounts). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupStatus status = GroupStatus.ACTIVE;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    StudyGroup(GroupData data) {
        update(data);
    }

    void update(GroupData data) {
        this.name = data.name();
        this.subject = data.subject();
        this.teacherId = data.teacherId();
        this.monthlyFee = data.monthlyFee();
        this.startDate = data.startDate();
        this.endDate = data.endDate();
        this.capacity = data.capacity();
    }

    void complete(LocalDate on) {
        this.status = GroupStatus.COMPLETED;
        if (endDate == null || endDate.isAfter(on)) {
            this.endDate = on.isBefore(startDate) ? startDate : on;
        }
    }
}
