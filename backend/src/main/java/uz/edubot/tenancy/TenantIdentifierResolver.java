package uz.edubot.tenancy;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Supplies the current center to Hibernate's {@code @TenantId} filter. Without a bound tenant it
 * returns a sentinel that matches no rows, so a forgotten context fails closed (empty results),
 * never open.
 */
@Component
class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.current().orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
