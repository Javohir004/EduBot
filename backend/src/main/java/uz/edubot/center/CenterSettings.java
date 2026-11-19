package uz.edubot.center;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "center_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CenterSettings {

    @Id
    private UUID centerId;

    @Column(nullable = false)
    private short paymentDueDay = 5;

    @Column(nullable = false)
    private boolean absenceNotificationsEnabled = true;

    @Column(nullable = false)
    private LocalTime quietHoursStart = LocalTime.of(22, 0);

    @Column(nullable = false)
    private LocalTime quietHoursEnd = LocalTime.of(8, 0);

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    CenterSettings(UUID centerId) {
        this.centerId = centerId;
    }
}
