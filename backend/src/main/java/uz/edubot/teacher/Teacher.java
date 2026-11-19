package uz.edubot.teacher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import uz.edubot.common.PersonStatus;
import uz.edubot.tenancy.TenantScopedEntity;

@Entity
@Table(name = "teacher")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Teacher extends TenantScopedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Telegram account, set when the teacher accepts an invite. */
    private UUID userId;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    private String subjects;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PersonStatus status = PersonStatus.ACTIVE;

    private Instant archivedAt;

    @Version
    private long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    Teacher(String fullName, String phone, String subjects) {
        update(fullName, phone, subjects);
    }

    void update(String fullName, String phone, String subjects) {
        this.fullName = fullName;
        this.phone = phone;
        this.subjects = subjects;
    }

    void archive(Instant now) {
        this.status = PersonStatus.ARCHIVED;
        this.archivedAt = now;
    }

    void restore() {
        this.status = PersonStatus.ACTIVE;
        this.archivedAt = null;
    }

}
