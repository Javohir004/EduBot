package uz.edubot.security.telegram;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bot credentials. The token is a secret and must only come from the environment.
 */
@Validated
@ConfigurationProperties("edubot.telegram")
public record TelegramProperties(
        @NotBlank String botToken,
        @NotBlank String botUsername,
        @NotNull Duration initDataMaxAge) {

    @Override
    public String toString() {
        return "TelegramProperties[botUsername=" + botUsername + ", initDataMaxAge=" + initDataMaxAge + "]";
    }
}
