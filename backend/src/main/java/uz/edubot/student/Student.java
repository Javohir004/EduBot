package uz.edubot.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "student")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends TenantScopedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate birthDate;

    private String phone;

    private String note;

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

    Student(StudentData data) {
        update(data);
    }

    void update(StudentData data) {
        this.firstName = data.firstName();
        this.lastName = data.lastName();
        this.birthDate = data.birthDate();
        this.phone = data.phone();
        this.note = data.note();
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
