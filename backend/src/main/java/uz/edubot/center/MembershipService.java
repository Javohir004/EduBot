package uz.edubot.center;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final CenterMembershipRepository repository;

    /**
     * Active memberships of the user in active centers only.
     */
    @Transactional(readOnly = true)
    public List<MembershipView> findActiveForUser(UUID userId) {
        return repository.findActiveViewsByUserId(userId);
    }

    /**
     * Returns the membership only if it belongs to the given user and is currently active.
     */
    @Transactional(readOnly = true)
    public Optional<MembershipView> findActiveForUser(UUID userId, UUID membershipId) {
        return findActiveForUser(userId).stream()
                .filter(m -> m.membershipId().equals(membershipId))
                .findFirst();
    }
}
