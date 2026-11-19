package uz.edubot.user;

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

/**
 * A Telegram account known to the platform. Rows are created through a native upsert
 * (see {@link AppUserRepository#insertIfAbsent}), so there is no id generator here.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private long telegramId;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    private String username;

    private String languageCode;

    private String verifiedPhone;

    @Enumerated(EnumType.STRING)
    private PlatformRole platformRole;

    private boolean botBlocked;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    void updateProfile(String firstName, String lastName, String username, String languageCode) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.languageCode = languageCode;
    }

    void changePlatformRole(PlatformRole platformRole) {
        this.platformRole = platformRole;
    }
}
