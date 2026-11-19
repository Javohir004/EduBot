package uz.edubot.center;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EducationCenterRepository extends JpaRepository<EducationCenter, UUID> {
}
