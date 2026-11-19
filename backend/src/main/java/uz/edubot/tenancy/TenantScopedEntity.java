package uz.edubot.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Base class for tenant-owned rows. Hibernate fills {@code center_id} on insert and adds
 * {@code center_id = :current} to HQL/Criteria queries. Native SQL is NOT filtered and must pass
 * the center id explicitly.
 */
@MappedSuperclass
@Getter
public abstract class TenantScopedEntity {

    @TenantId
    @Column(name = "center_id", nullable = false, updatable = false)
    private UUID centerId;
}
