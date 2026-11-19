package uz.edubot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Long polling for the MVP (single instance, no public webhook endpoint needed).
 * Disabled with {@code TELEGRAM_BOT_ENABLED=false}, e.g. in tests or a second instance.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class BotConfig {

    @Bean
    TelegramClient telegramClient(@Value("${edubot.telegram.bot-token}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "edubot.bot.enabled", havingValue = "true", matchIfMissing = true)
    TelegramBotsLongPollingApplication botApplication() {
        return new TelegramBotsLongPollingApplication();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "edubot.bot.enabled", havingValue = "true", matchIfMissing = true)
    static class BotStarter {

        private final TelegramBotsLongPollingApplication application;
        private final BotUpdateHandler handler;
        private final String botToken;

        BotStarter(TelegramBotsLongPollingApplication application, BotUpdateHandler handler,
                   @Value("${edubot.telegram.bot-token}") String botToken) {
            this.application = application;
            this.handler = handler;
            this.botToken = botToken;
        }

        /**
         * Polling starts only once the application is fully up: if startup fails (port in use,
         * bad config) no updates are consumed and lost by a half-started instance.
         */
        @EventListener(ApplicationReadyEvent.class)
        void start() throws TelegramApiException {
            application.registerBot(botToken, handler);
            log.info("Telegram bot long polling started");
        }
    }
}
