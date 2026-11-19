package uz.edubot.student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.edubot.common.PersonStatus;
import uz.edubot.tenancy.TenantScopedRepository;

/** All queries are JPQL, so the Hibernate tenant filter applies to each of them. */
interface StudentRepository extends TenantScopedRepository<Student> {

    @Query("""
            select s from Student s
            where s.status = :status
              and (lower(concat(s.firstName, ' ', s.lastName)) like :pattern
                   or lower(concat(s.lastName, ' ', s.firstName)) like :pattern
                   or coalesce(s.phone, '') like :pattern)
            """)
    Page<Student> search(@Param("status") PersonStatus status, @Param("pattern") String pattern, Pageable pageable);

    long countByStatus(PersonStatus status);
}

interface GuardianRepository extends TenantScopedRepository<Guardian> {

    @Query("select g from Guardian g where g.phone = :phone")
    Optional<Guardian> findByPhone(@Param("phone") String phone);

    @Query("select g from Guardian g where g.id in :ids")
    List<Guardian> findByIds(@Param("ids") Collection<UUID> ids);

    /** Native with an explicit center id: runs inside the invite-accept session, which has no tenant. */
    @Modifying
    @Query(value = """
            update guardian set user_id = :userId, updated_at = now()
            where id = :guardianId and center_id = :centerId and user_id is null
            """, nativeQuery = true)
    int linkUser(@Param("centerId") UUID centerId, @Param("guardianId") UUID guardianId, @Param("userId") UUID userId);
}

interface StudentGuardianRepository extends JpaRepository<StudentGuardian, StudentGuardian.Key> {

    @Query("select sg from StudentGuardian sg where sg.studentId = :studentId order by sg.createdAt")
    List<StudentGuardian> findByStudent(@Param("studentId") UUID studentId);

    @Query("select sg from StudentGuardian sg where sg.studentId = :studentId and sg.guardianId = :guardianId")
    Optional<StudentGuardian> findLink(@Param("studentId") UUID studentId, @Param("guardianId") UUID guardianId);
}
