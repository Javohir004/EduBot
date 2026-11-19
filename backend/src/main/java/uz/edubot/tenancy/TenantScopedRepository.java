package uz.edubot.tenancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * Base repository for {@link TenantScopedEntity}. {@code findById} is routed through JPQL so the
 * Hibernate tenant filter always applies (a plain {@code EntityManager.find} may be served from
 * the persistence context without it).
 */
@NoRepositoryBean
public interface TenantScopedRepository<T extends TenantScopedEntity> extends JpaRepository<T, UUID> {

    @Override
    @Query("select e from #{#entityName} e where e.id = :id")
    Optional<T> findById(@Param("id") UUID id);
}
