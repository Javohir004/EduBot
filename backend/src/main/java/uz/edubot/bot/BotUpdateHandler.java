package uz.edubot.bot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.edubot.common.error.ApiException;
import uz.edubot.invite.AcceptedInvite;
import uz.edubot.invite.InviteService;
import uz.edubot.user.TelegramProfile;

/**
 * Telegram adapter: parses updates and delegates to services. No business rules live here.
 *
 * <p>Invite flow: {@code /start inv_<token>} remembers the token for this chat, asks for the
 * contact, and the shared contact (verified by Telegram) completes the invite.
 */
@Slf4j
@Component
class BotUpdateHandler implements LongPollingSingleThreadUpdateConsumer {

    static final Duration PENDING_TTL = Duration.ofMinutes(15);
    private static final String START = "/start";
    private static final String INVITE_PREFIX = "inv_";

    private final TelegramClient telegram;
    private final InviteService inviteService;
    private final Clock clock;
    private final String miniAppUrl;
    /** chatId -> invite start parameter. In memory: after a restart the user just reopens the link. */
    private final Map<Long, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    BotUpdateHandler(TelegramClient telegram, InviteService inviteService, Clock clock,
                     @Value("${edubot.app.public-url:}") String miniAppUrl) {
        this.telegram = telegram;
        this.inviteService = inviteService;
        this.clock = clock;
        this.miniAppUrl = miniAppUrl;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().isUserMessage()) {
            return;
        }
        Message message = update.getMessage();
        try {
            if (message.hasContact()) {
                handleContact(message);
            } else if (message.hasText() && message.getText().startsWith(START)) {
                handleStart(message);
            } else {
                send(message.getChatId(), BotMessages.HELP, null);
            }
        } catch (RuntimeException e) {
            log.error("Failed to handle update {}", update.getUpdateId(), e);
            send(message.getChatId(), BotMessages.UNEXPECTED_ERROR, null);
        }
    }

    private void handleStart(Message message) {
        String[] parts = message.getText().strip().split("\\s+", 2);
        String parameter = parts.length > 1 ? parts[1] : "";
        if (parameter.startsWith(INVITE_PREFIX)) {
            pendingInvites.put(message.getChatId(), new PendingInvite(parameter, clock.instant()));
            send(message.getChatId(), BotMessages.SHARE_CONTACT_REQUEST, contactKeyboard());
            return;
        }
        send(message.getChatId(), BotMessages.WELCOME, null);
        sendOpenAppButton(message.getChatId());
    }

    private void handleContact(Message message) {
        long chatId = message.getChatId();
        Contact contact = message.getContact();
        User from = message.getFrom();
        // Only a contact the user shared about themselves proves they own the number.
        if (contact.getUserId() == null || !contact.getUserId().equals(from.getId())) {
            send(chatId, BotMessages.NOT_OWN_CONTACT, contactKeyboard());
            return;
        }
        PendingInvite pending = pendingInvites.remove(chatId);
        if (pending == null || pending.createdAt().plus(PENDING_TTL).isBefore(clock.instant())) {
            send(chatId, BotMessages.NO_PENDING_INVITE, removeKeyboard());
            return;
        }
        TelegramProfile profile = new TelegramProfile(from.getId(), from.getFirstName(), from.getLastName(),
                from.getUserName(), from.getLanguageCode());
        try {
            AcceptedInvite accepted = inviteService.accept(pending.startParameter(), profile, contact.getPhoneNumber());
            send(chatId, BotMessages.linked(accepted.centerName(), accepted.role()), removeKeyboard());
            sendOpenAppButton(chatId);
        } catch (ApiException e) {
            send(chatId, messageFor(e.getCode()), removeKeyboard());
        }
    }

    private static String messageFor(String code) {
        return switch (code) {
            case "INVITE_NOT_FOUND" -> BotMessages.INVITE_NOT_FOUND;
            case "INVITE_ALREADY_USED" -> BotMessages.INVITE_USED;
            case "INVITE_EXPIRED" -> BotMessages.INVITE_EXPIRED;
            case "INVITE_PHONE_MISMATCH" -> BotMessages.PHONE_MISMATCH;
            default -> BotMessages.UNEXPECTED_ERROR;
        };
    }

    private void sendOpenAppButton(long chatId) {
        if (miniAppUrl == null || miniAppUrl.isBlank()) {
            send(chatId, BotMessages.NO_APP_URL, null);
            return;
        }
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(BotMessages.OPEN_APP)
                .webApp(new WebAppInfo(miniAppUrl))
                .build();
        send(chatId, BotMessages.OPEN_APP_HINT, new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button))));
    }

    private static ReplyKeyboardMarkup contactKeyboard() {
        KeyboardButton button = KeyboardButton.builder()
                .text(BotMessages.SHARE_CONTACT_BUTTON)
                .requestContact(true)
                .build();
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(button))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
    }

    private static ReplyKeyboardRemove removeKeyboard() {
        return ReplyKeyboardRemove.builder().removeKeyboard(true).build();
    }

    private void send(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = SendMessage.builder().chatId(chatId).text(text).replyMarkup(keyboard).build();
        try {
            telegram.execute(message);
        } catch (TelegramApiException e) {
            log.warn("Failed to send message to chat {}: {}", chatId, e.getMessage());
        }
    }

    record PendingInvite(String startParameter, Instant createdAt) {
    }
}
