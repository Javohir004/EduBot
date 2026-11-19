package uz.edubot.notification;

/**
 * Delivery channel port. Implemented by the bot module (Telegram), replaced by a fake in tests.
 */
public interface NotificationSender {

    SendResult send(long telegramChatId, String text);

    sealed interface SendResult {
        record Sent() implements SendResult {
        }

        /** The user blocked the bot or deleted the chat; retrying will not help. */
        record Blocked() implements SendResult {
        }

        /** Telegram asked us to slow down. */
        record RetryAfter(int seconds) implements SendResult {
        }

        record Failed(String error) implements SendResult {
        }
    }
}
