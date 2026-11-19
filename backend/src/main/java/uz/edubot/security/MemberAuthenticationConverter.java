package uz.edubot.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import uz.edubot.center.MembershipService;
import uz.edubot.center.MembershipView;
import uz.edubot.user.AppUser;
import uz.edubot.user.PlatformRole;
import uz.edubot.user.UserService;

/**
 * Turns a valid JWT into a {@link CurrentMember}. Roles and membership status are read from the
 * database here, so revoking a membership takes effect on the next request.
 */
@Component
@RequiredArgsConstructor
class MemberAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;
    private final MembershipService membershipService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUuid(jwt.getSubject());
        AppUser user = userService.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));

        String mid = jwt.getClaimAsString(TokenService.MEMBERSHIP_CLAIM);
        MembershipView membership = null;
        if (mid != null) {
            membership = membershipService.findActiveForUser(userId, parseUuid(mid))
                    .orElseThrow(() -> new InvalidBearerTokenException("Membership is no longer active"));
        }

        Set<Permission> permissions = membership == null
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(Permission.forRole(membership.role()));
        if (user.getPlatformRole() == PlatformRole.SUPER_ADMIN) {
            permissions.add(Permission.PLATFORM_MANAGE);
        }

        CurrentMember member = new CurrentMember(userId, user.getPlatformRole(),
                membership == null ? null : membership.membershipId(),
                membership == null ? null : membership.centerId(),
                membership == null ? null : membership.role(),
                Set.copyOf(permissions));
        return new MemberAuthenticationToken(jwt, member, toAuthorities(member));
    }

    private static Collection<GrantedAuthority> toAuthorities(CurrentMember member) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        member.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        if (member.role() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + member.role().name()));
        }
        return authorities;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Malformed token");
        }
    }

    static final class MemberAuthenticationToken extends AbstractAuthenticationToken {

        private final Jwt jwt;
        private final CurrentMember member;

        MemberAuthenticationToken(Jwt jwt, CurrentMember member, Collection<GrantedAuthority> authorities) {
            super(authorities);
            this.jwt = jwt;
            this.member = member;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return jwt.getTokenValue();
        }

        @Override
        public CurrentMember getPrincipal() {
            return member;
        }

        @Override
        public String getName() {
            return member.userId().toString();
        }
    }
}
