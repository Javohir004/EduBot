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
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
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
        private final TelegramClient telegram;
        private final String botToken;
        private final String miniAppUrl;

        BotStarter(TelegramBotsLongPollingApplication application, BotUpdateHandler handler, TelegramClient telegram,
                   @Value("${edubot.telegram.bot-token}") String botToken,
                   @Value("${edubot.app.public-url:}") String miniAppUrl) {
            this.application = application;
            this.handler = handler;
            this.telegram = telegram;
            this.botToken = botToken;
            this.miniAppUrl = miniAppUrl;
        }

        /**
         * Polling starts only once the application is fully up: if startup fails (port in use,
         * bad config) no updates are consumed and lost by a half-started instance.
         */
        @EventListener(ApplicationReadyEvent.class)
        void start() throws TelegramApiException {
            application.registerBot(botToken, handler);
            log.info("Telegram bot long polling started");
            updateMenuButton();
        }

        /**
         * Points the bot's default menu button at the current Mini App URL, so a changed URL
         * (e.g. a new dev tunnel) needs no manual step in @BotFather.
         */
        private void updateMenuButton() {
            if (miniAppUrl == null || !miniAppUrl.startsWith("https://")) {
                log.warn("MINI_APP_URL is not an https URL: the bot menu button was not updated");
                return;
            }
            try {
                telegram.execute(SetChatMenuButton.builder()
                        .menuButton(MenuButtonWebApp.builder().text("EduBot").webAppInfo(new WebAppInfo(miniAppUrl)).build())
                        .build());
                log.info("Bot menu button points to {}", miniAppUrl);
            } catch (TelegramApiException e) {
                log.warn("Could not update the bot menu button: {}", e.getMessage());
            }
        }
    }
}
