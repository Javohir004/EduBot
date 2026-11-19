package uz.edubot.invite;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InviteRepository extends JpaRepository<Invite, UUID> {

    /** Row lock so two concurrent accepts of the same token cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.tokenHash = :tokenHash")
    Optional<Invite> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<Invite> findByIdAndCenterId(UUID id, UUID centerId);

    @Query("""
            select i from Invite i
            where i.centerId = :centerId and i.usedAt is null and i.revokedAt is null and i.expiresAt > :now
            order by i.createdAt desc
            """)
    List<Invite> findPending(@Param("centerId") UUID centerId, @Param("now") Instant now);
}
