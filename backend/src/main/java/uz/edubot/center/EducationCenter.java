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
 * Tenant root. Every tenant-scoped row references a center through {@code center_id}.
 */
@Entity
@Table(name = "education_center")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationCenter {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String phone;

    private String address;

    @Column(nullable = false)
    private String timezone = "Asia/Tashkent";

    @Column(nullable = false, length = 3)
    private String currency = "UZS";

    @Column(nullable = false)
    private String defaultLanguage = "uz";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CenterStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public EducationCenter(String name, String phone, String address, CenterStatus status) {
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.status = status;
    }

    void changeStatus(CenterStatus status) {
        this.status = status;
    }
}
