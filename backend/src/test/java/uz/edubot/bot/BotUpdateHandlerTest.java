package uz.edubot.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.edubot.center.MembershipRole;
import uz.edubot.invite.AcceptedInvite;
import uz.edubot.invite.InviteService;
import uz.edubot.user.TelegramProfile;
import uz.edubot.user.UserService;

class BotUpdateHandlerTest {

    private static final long USER_ID = 555L;

    private final TelegramClient telegram = mock(TelegramClient.class);
    private final InviteService inviteService = mock(InviteService.class);
    private final BotUpdateHandler handler = new BotUpdateHandler(telegram, inviteService, mock(UserService.class),
            Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC), "https://app.example.test");

    @Test
    void ownContactCompletesPendingInvite() throws Exception {
        when(inviteService.accept(eq("inv_token"), any(TelegramProfile.class), eq("998901112233")))
                .thenReturn(new AcceptedInvite(UUID.randomUUID(), "Alpha", MembershipRole.TEACHER));

        handler.consume(textUpdate("/start inv_token"));
        handler.consume(contactUpdate(USER_ID, "998901112233"));

        verify(inviteService).accept(eq("inv_token"), any(TelegramProfile.class), eq("998901112233"));
        assertThat(sentTexts()).anyMatch(t -> t.contains("«Alpha»") && t.contains("o'qituvchi"));
    }

    @Test
    void contactOfAnotherPersonIsRejected() throws Exception {
        handler.consume(textUpdate("/start inv_token"));
        handler.consume(contactUpdate(777L, "998901112233"));

        verify(inviteService, never()).accept(any(), any(), any());
        assertThat(sentTexts()).contains(BotMessages.NOT_OWN_CONTACT);
    }

    @Test
    void contactWithoutInviteLinkIsIgnored() throws Exception {
        handler.consume(contactUpdate(USER_ID, "998901112233"));

        verify(inviteService, never()).accept(any(), any(), any());
        assertThat(sentTexts()).contains(BotMessages.NO_PENDING_INVITE);
    }

    @Test
    void plainStartShowsOpenAppButton() throws Exception {
        handler.consume(textUpdate("/start"));

        assertThat(sentTexts()).contains(BotMessages.WELCOME, BotMessages.OPEN_APP_HINT);
    }

    private java.util.List<String> sentTexts() throws Exception {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegram, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues().stream().map(SendMessage::getText).toList();
    }

    private static Update textUpdate(String text) {
        Message message = baseMessage();
        message.setText(text);
        return update(message);
    }

    private static Update contactUpdate(long contactUserId, String phone) {
        Message message = baseMessage();
        message.setContact(Contact.builder().phoneNumber(phone).firstName("Ali").userId(contactUserId).build());
        return update(message);
    }

    private static Message baseMessage() {
        Message message = new Message();
        message.setMessageId(1);
        message.setChat(Chat.builder().id(USER_ID).type("private").build());
        message.setFrom(User.builder().id(USER_ID).firstName("Ali").isBot(false).build());
        return message;
    }

    private static Update update(Message message) {
        Update update = new Update();
        update.setUpdateId(1);
        update.setMessage(message);
        return update;
    }
}
