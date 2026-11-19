package uz.edubot.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import uz.edubot.tenancy.TenantScopedEntity;

/** One weekly lesson slot of a group. */
@Entity
@Table(name = "group_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupSchedule extends TenantScopedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID groupId;

    /** ISO day of week: 1 = Monday ... 7 = Sunday. */
    @Column(nullable = false)
    private short dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private String room;

    GroupSchedule(UUID groupId, DayOfWeek day, LocalTime startTime, LocalTime endTime, String room) {
        this.groupId = groupId;
        this.dayOfWeek = (short) day.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
    }
}
