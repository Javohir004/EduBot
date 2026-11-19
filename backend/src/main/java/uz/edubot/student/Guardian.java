package uz.edubot.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import uz.edubot.tenancy.TenantScopedEntity;

/**
 * Parent or other guardian. Unique per phone within a center, so siblings share one guardian.
 */
@Entity
@Table(name = "guardian")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guardian extends TenantScopedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    Guardian(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

}
