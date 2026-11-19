package uz.edubot.center;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CenterSettingsRepository extends JpaRepository<CenterSettings, UUID> {
}
