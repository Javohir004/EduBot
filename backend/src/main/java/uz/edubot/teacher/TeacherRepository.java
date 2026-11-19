package uz.edubot.teacher;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.edubot.common.PersonStatus;
import uz.edubot.tenancy.TenantScopedRepository;

/** All queries are JPQL, so the tenant filter applies to each of them. */
interface TeacherRepository extends TenantScopedRepository<Teacher> {

    @Query("""
            select t from Teacher t
            where t.status = :status
              and (lower(t.fullName) like :pattern or coalesce(t.phone, '') like :pattern)
            """)
    Page<Teacher> search(@Param("status") PersonStatus status, @Param("pattern") String pattern, Pageable pageable);

    long countByStatus(PersonStatus status);

    /** Native with an explicit center id: runs inside the invite-accept session, which has no tenant. */
    @Modifying
    @Query(value = """
            update teacher set user_id = :userId, updated_at = now()
            where id = :teacherId and center_id = :centerId and user_id is null
            """, nativeQuery = true)
    int linkUser(@Param("centerId") UUID centerId, @Param("teacherId") UUID teacherId, @Param("userId") UUID userId);
}
