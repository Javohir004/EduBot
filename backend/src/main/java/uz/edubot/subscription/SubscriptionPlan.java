package uz.edubot.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscription_plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPlan {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "public_plan", nullable = false)
    private boolean publicPlan;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
