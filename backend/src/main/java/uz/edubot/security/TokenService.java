package uz.edubot.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import uz.edubot.center.MembershipView;

/**
 * Issues access tokens. Tokens carry identifiers only; roles are always resolved from the database.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    static final String MEMBERSHIP_CLAIM = "mid";
    static final String CENTER_CLAIM = "cid";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * @param membership active membership, or {@code null} for a token that is not bound to a center
     */
    public AccessToken issue(UUID userId, MembershipView membership) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt);
        if (membership != null) {
            claims.claim(MEMBERSHIP_CLAIM, membership.membershipId().toString());
            claims.claim(CENTER_CLAIM, membership.centerId().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(token, expiresAt);
    }
}
