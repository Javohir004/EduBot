package uz.edubot.subscription;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("""
            select new uz.edubot.subscription.SubscriptionView(s.centerId, p.code, p.name, s.status, s.endsAt)
            from Subscription s
            join SubscriptionPlan p on p.id = s.planId
            where s.centerId in :centerIds
              and s.status in (uz.edubot.subscription.SubscriptionStatus.TRIAL,
                               uz.edubot.subscription.SubscriptionStatus.ACTIVE)
            """)
    List<SubscriptionView> findCurrentByCenterIds(@Param("centerIds") Collection<UUID> centerIds);
}
