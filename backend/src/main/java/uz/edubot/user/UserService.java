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
        repository.insertIfAbsent(profile.telegramId(), profile.firstName());
        AppUser user = repository.findByTelegramId(profile.telegramId())
                .orElseThrow(() -> new IllegalStateException("User row missing after upsert"));
        user.updateProfile(profile.firstName(), profile.lastName(), profile.username(), profile.languageCode());
        user.changePlatformRole(platformAdmin ? PlatformRole.SUPER_ADMIN : null);
        return user;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findById(UUID id) {
        return repository.findById(id);
    }
}
