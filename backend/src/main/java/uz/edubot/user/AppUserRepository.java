package uz.edubot.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByTelegramId(long telegramId);

    /**
     * Race-safe creation: two concurrent first logins of the same Telegram user produce one row.
     */
    @Modifying
    @Query(value = """
            insert into app_user (id, telegram_id, first_name, created_at, updated_at)
            values (uuidv7(), :telegramId, :firstName, now(), now())
            on conflict (telegram_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("telegramId") long telegramId, @Param("firstName") String firstName);
}
