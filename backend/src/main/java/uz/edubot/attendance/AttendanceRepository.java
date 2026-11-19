package uz.edubot.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lessons and attendance marks. Plain SQL with an explicit center id in every statement
 * (native SQL is not covered by the Hibernate tenant filter). Upserts make concurrent and
 * repeated marking safe without application-level locking.
 */
@Repository
@RequiredArgsConstructor
class AttendanceRepository {

    private final JdbcTemplate jdbc;

    record LessonRow(UUID id, UUID groupId, LocalDate date, LocalTime startTime) {
    }

    record MarkRow(UUID lessonId, UUID studentId, AttendanceStatus status) {
    }

    /** Get-or-create: two teachers opening the same lesson at once get the same row. */
    UUID openLesson(UUID centerId, UUID groupId, LocalDate date, LocalTime startTime, UUID userId) {
        jdbc.update("""
                insert into lesson (id, center_id, group_id, lesson_date, start_time, status, created_by,
                                    created_at, updated_at)
                values (uuidv7(), ?, ?, ?, ?, 'HELD', ?, now(), now())
                on conflict (group_id, lesson_date, start_time) do nothing
                """, centerId, groupId, date, startTime, userId);
        return jdbc.queryForObject(
                "select id from lesson where center_id = ? and group_id = ? and lesson_date = ? and start_time = ?",
                UUID.class, centerId, groupId, date, startTime);
    }

    Optional<LessonRow> findLesson(UUID centerId, UUID lessonId) {
        return jdbc.query("select id, group_id, lesson_date, start_time from lesson where center_id = ? and id = ?",
                (rs, i) -> new LessonRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalTime.class)),
                centerId, lessonId).stream().findFirst();
    }

    List<LessonRow> findLessons(UUID centerId, Collection<UUID> groupIds, LocalDate from, LocalDate to) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                        select id, group_id, lesson_date, start_time from lesson
                        where center_id = ? and group_id = any(?) and lesson_date between ? and ?
                        order by lesson_date, start_time
                        """,
                (rs, i) -> new LessonRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalTime.class)),
                centerId, groupIds.toArray(UUID[]::new), from, to);
    }

    void upsertMark(UUID centerId, UUID lessonId, UUID studentId, AttendanceStatus status, UUID markedBy, Instant now) {
        jdbc.update("""
                insert into attendance (id, center_id, lesson_id, student_id, status, marked_by, marked_at)
                values (uuidv7(), ?, ?, ?, ?, ?, ?)
                on conflict (lesson_id, student_id) do update
                    set status = excluded.status, marked_by = excluded.marked_by, marked_at = excluded.marked_at
                """, centerId, lessonId, studentId, status.name(), markedBy, java.sql.Timestamp.from(now));
    }

    List<MarkRow> findMarks(UUID centerId, Collection<UUID> lessonIds) {
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select lesson_id, student_id, status from attendance where center_id = ? and lesson_id = any(?)",
                (rs, i) -> new MarkRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        AttendanceStatus.valueOf(rs.getString(3))),
                centerId, lessonIds.toArray(UUID[]::new));
    }
}
