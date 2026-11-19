package uz.edubot.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.edubot.notification.NotificationSender;

/** Telegram implementation of the notification channel. */
@Component
@RequiredArgsConstructor
class TelegramNotificationSender implements NotificationSender {

    private final TelegramClient telegram;

    @Override
    public SendResult send(long telegramChatId, String text) {
        try {
            telegram.execute(SendMessage.builder().chatId(telegramChatId).text(text).build());
            return new SendResult.Sent();
        } catch (TelegramApiRequestException e) {
            Integer code = e.getErrorCode();
            if (code != null && code == 403) {
                return new SendResult.Blocked();
            }
            if (code != null && code == 429 && e.getParameters() != null && e.getParameters().getRetryAfter() != null) {
                return new SendResult.RetryAfter(e.getParameters().getRetryAfter());
            }
            return new SendResult.Failed(e.getApiResponse() != null ? e.getApiResponse() : e.getMessage());
        } catch (TelegramApiException e) {
            return new SendResult.Failed(e.getMessage());
        }
    }
}
