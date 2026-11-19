package uz.edubot.security.telegram;

import java.time.Instant;

/**
 * initData whose signature and freshness have been verified.
 */
public record TelegramInitData(TelegramUser user, Instant authDate, String startParam) {
}
