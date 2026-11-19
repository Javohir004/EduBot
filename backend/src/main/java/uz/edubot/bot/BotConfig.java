package uz.edubot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    TelegramBotsLongPollingApplication botApplication(@Value("${edubot.telegram.bot-token}") String botToken,
                                                      BotUpdateHandler handler) throws TelegramApiException {
        TelegramBotsLongPollingApplication application = new TelegramBotsLongPollingApplication();
        application.registerBot(botToken, handler);
        log.info("Telegram bot long polling started");
        return application;
    }
}
