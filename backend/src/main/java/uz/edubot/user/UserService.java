package uz.edubot.user;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository repository;

    /**
     * Creates the user on first login and refreshes profile data on every login.
     *
     * @param platformAdmin whether the account is listed as a platform admin in configuration;
     *                      configuration is the source of truth, so the role is also revoked here
     */
    @Transactional
    public AppUser upsertFromTelegram(TelegramProfile profile, boolean platformAdmin) {
        AppUser user = upsertFromTelegram(profile);
        user.changePlatformRole(platformAdmin ? PlatformRole.SUPER_ADMIN : null);
        return user;
    }

    /**
     * Creates or refreshes the user without touching the platform role (used by the bot).
     */
    @Transactional
    public AppUser upsertFromTelegram(TelegramProfile profile) {
        repository.insertIfAbsent(profile.telegramId(), profile.firstName());
        AppUser user = repository.findByTelegramId(profile.telegramId())
                .orElseThrow(() -> new IllegalStateException("User row missing after upsert"));
        user.updateProfile(profile.firstName(), profile.lastName(), profile.username(), profile.languageCode());
        return user;
    }

    /**
     * Stores a phone number that Telegram verified (shared via the contact button).
     */
    @Transactional
    public void markPhoneVerified(UUID userId, String normalizedPhone) {
        repository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId))
                .verifyPhone(normalizedPhone);
    }

    /** Returns the user, creating a minimal one if absent; an existing profile is not modified. */
    @Transactional
    public AppUser ensureExists(long telegramId, String firstName) {
        repository.insertIfAbsent(telegramId, firstName);
        return repository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalStateException("User row missing after insert"));
    }

    /** The user pressed /start: the bot can message them again. */
    @Transactional
    public void markBotStarted(TelegramProfile profile) {
        upsertFromTelegram(profile).changeBotBlocked(false);
    }

    /** The user blocked the bot: Telegram rejects messages until they press /start again. */
    @Transactional
    public void markBotBlocked(UUID userId, boolean blocked) {
        repository.findById(userId).ifPresent(u -> u.changeBotBlocked(blocked));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findById(UUID id) {
        return repository.findById(id);
    }
}
