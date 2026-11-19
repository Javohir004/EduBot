package uz.edubot.center;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.common.error.ApiException;

@Service
@RequiredArgsConstructor
public class CenterService {

    private final EducationCenterRepository centers;
    private final CenterSettingsRepository settings;

    /**
     * Creates an active center together with its default settings.
     */
    @Transactional
    public EducationCenter create(String name, String phone, String address) {
        EducationCenter center = centers.save(new EducationCenter(name.strip(), phone, address, CenterStatus.ACTIVE));
        settings.save(new CenterSettings(center.getId()));
        return center;
    }

    @Transactional(readOnly = true)
    public Optional<EducationCenter> findById(UUID id) {
        return centers.findById(id);
    }

    @Transactional(readOnly = true)
    public CenterSettings settings(UUID centerId) {
        return settings.findById(centerId)
                .orElseThrow(() -> new IllegalStateException("Center without settings: " + centerId));
    }

    @Transactional
    public CenterSettings updateSettings(UUID centerId, int paymentDueDay, boolean absenceNotifications,
                                         boolean paymentReminders) {
        CenterSettings current = settings(centerId);
        current.update(paymentDueDay, absenceNotifications, paymentReminders);
        return current;
    }

    @Transactional(readOnly = true)
    public Page<EducationCenter> findAll(Pageable pageable) {
        return centers.findAll(pageable);
    }

    @Transactional
    public EducationCenter changeStatus(UUID id, CenterStatus status) {
        EducationCenter center = centers.findById(id)
                .orElseThrow(() -> ApiException.notFound("CENTER_NOT_FOUND", "Center not found"));
        center.changeStatus(status);
        return center;
    }
}
