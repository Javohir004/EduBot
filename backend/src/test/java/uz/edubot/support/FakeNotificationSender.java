package uz.edubot.support;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uz.edubot.notification.NotificationSender;

/** Records messages instead of calling Telegram; chats in {@link #blocked} behave like a blocked bot. */
public class FakeNotificationSender implements NotificationSender {

    public record Sent(long chatId, String text) {
    }

    public final List<Sent> sent = new CopyOnWriteArrayList<>();
    public final Set<Long> blocked = ConcurrentHashMap.newKeySet();

    @Override
    public SendResult send(long telegramChatId, String text) {
        if (blocked.contains(telegramChatId)) {
            return new SendResult.Blocked();
        }
        sent.add(new Sent(telegramChatId, text));
        return new SendResult.Sent();
    }

    public void reset() {
        sent.clear();
        blocked.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        FakeNotificationSender fakeNotificationSender() {
            return new FakeNotificationSender();
        }
    }
}
