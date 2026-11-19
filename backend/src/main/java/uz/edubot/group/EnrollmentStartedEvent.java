package uz.edubot.group;

import java.time.LocalDate;
import java.util.UUID;

/** Published synchronously inside the enrolling transaction (billing charges the first month). */
public record EnrollmentStartedEvent(UUID enrollmentId, UUID studentId, UUID groupId, LocalDate joinedOn) {
}
