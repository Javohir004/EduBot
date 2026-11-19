package uz.edubot.center;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CenterMembershipRepository extends JpaRepository<CenterMembership, UUID> {

    Optional<CenterMembership> findByCenterIdAndUserIdAndRole(UUID centerId, UUID userId, MembershipRole role);

    @Query("""
            select new uz.edubot.center.MembershipView(m.id, c.id, c.name, m.role)
            from CenterMembership m
            join EducationCenter c on c.id = m.centerId
            where m.userId = :userId
              and m.status = uz.edubot.center.MembershipStatus.ACTIVE
              and c.status = uz.edubot.center.CenterStatus.ACTIVE
            order by c.name, m.role
            """)
    List<MembershipView> findActiveViewsByUserId(@Param("userId") UUID userId);
}
