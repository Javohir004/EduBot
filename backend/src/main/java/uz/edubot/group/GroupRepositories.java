package uz.edubot.group;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.edubot.tenancy.TenantScopedRepository;

/** All queries are JPQL, so the Hibernate tenant filter applies to each of them. */
interface StudyGroupRepository extends TenantScopedRepository<StudyGroup> {

    @Query("""
            select g from StudyGroup g
            where g.status = :status and lower(g.name) like :pattern
              and (:allTeachers = true or g.teacherId = :teacherId)
            """)
    Page<StudyGroup> search(@Param("status") GroupStatus status, @Param("pattern") String pattern,
                            @Param("allTeachers") boolean allTeachers, @Param("teacherId") UUID teacherId,
                            Pageable pageable);

    /** Row lock that serializes enrollments into the same group (capacity check). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from StudyGroup g where g.id = :id")
    Optional<StudyGroup> findByIdForUpdate(@Param("id") UUID id);

    @Query("select g from StudyGroup g where g.id in :ids")
    List<StudyGroup> findByIds(@Param("ids") Collection<UUID> ids);

    long countByStatus(GroupStatus status);
}

interface GroupScheduleRepository extends TenantScopedRepository<GroupSchedule> {

    @Query("select s from GroupSchedule s where s.groupId = :groupId order by s.dayOfWeek, s.startTime")
    List<GroupSchedule> findByGroup(@Param("groupId") UUID groupId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupSchedule s where s.groupId = :groupId")
    void deleteByGroup(@Param("groupId") UUID groupId);
}

interface EnrollmentRepository extends TenantScopedRepository<Enrollment> {

    @Query("""
            select e from Enrollment e
            where e.groupId = :groupId and e.status = uz.edubot.group.EnrollmentStatus.ACTIVE
            """)
    List<Enrollment> findActiveByGroup(@Param("groupId") UUID groupId);

    @Query("""
            select count(e) from Enrollment e
            where e.groupId = :groupId and e.status = uz.edubot.group.EnrollmentStatus.ACTIVE
            """)
    long countActiveByGroup(@Param("groupId") UUID groupId);

    @Query("""
            select e.groupId, count(e) from Enrollment e
            where e.groupId in :groupIds and e.status = uz.edubot.group.EnrollmentStatus.ACTIVE
            group by e.groupId
            """)
    List<Object[]> countActiveByGroups(@Param("groupIds") Collection<UUID> groupIds);

    @Query("""
            select e from Enrollment e
            where e.studentId = :studentId and e.groupId = :groupId
              and e.status = uz.edubot.group.EnrollmentStatus.ACTIVE
            """)
    Optional<Enrollment> findActive(@Param("studentId") UUID studentId, @Param("groupId") UUID groupId);

    @Query("select e from Enrollment e where e.studentId = :studentId order by e.status, e.joinedOn desc")
    List<Enrollment> findByStudent(@Param("studentId") UUID studentId);

    @Query("""
            select e from Enrollment e
            where e.studentId = :studentId and e.status = uz.edubot.group.EnrollmentStatus.ACTIVE
            """)
    List<Enrollment> findActiveByStudent(@Param("studentId") UUID studentId);
}
