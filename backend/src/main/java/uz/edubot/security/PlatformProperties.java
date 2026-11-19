package uz.edubot.security;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telegram user ids that are granted {@code SUPER_ADMIN}. Configuration is the source of truth.
 */
@ConfigurationProperties("edubot.platform")
public record PlatformProperties(Set<Long> adminTelegramIds) {

    public PlatformProperties {
        adminTelegramIds = adminTelegramIds == null ? Set.of() : Set.copyOf(adminTelegramIds);
    }

    public boolean isPlatformAdmin(long telegramId) {
        return adminTelegramIds.contains(telegramId);
    }
}
