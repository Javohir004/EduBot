package uz.edubot.user;

/**
 * Verified Telegram profile data used to create or refresh a user.
 */
public record TelegramProfile(long telegramId, String firstName, String lastName, String username,
                              String languageCode) {
}
