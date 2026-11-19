package uz.edubot.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import uz.edubot.tenancy.TenantScopedEntity;

@Entity
@Table(name = "student_guardian")
@IdClass(StudentGuardian.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentGuardian extends TenantScopedEntity {

    @Id
    private UUID studentId;

    @Id
    private UUID guardianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GuardianRelation relation;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    StudentGuardian(UUID studentId, UUID guardianId, GuardianRelation relation) {
        this.studentId = studentId;
        this.guardianId = guardianId;
        this.relation = relation;
    }

    record Key(UUID studentId, UUID guardianId) implements Serializable {
    }
}
