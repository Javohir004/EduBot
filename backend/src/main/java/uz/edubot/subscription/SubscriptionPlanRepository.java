package uz.edubot.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByCodeAndActiveTrue(String code);

    List<SubscriptionPlan> findByActiveTrueOrderBySortOrder();
}
