package uz.edubot.security.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.security.AuthenticatedUser;
import uz.edubot.security.web.AuthDtos.AuthResponse;
import uz.edubot.security.web.AuthDtos.MeResponse;
import uz.edubot.security.web.AuthDtos.SessionRequest;
import uz.edubot.security.web.AuthDtos.TelegramAuthRequest;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/telegram")
    AuthResponse telegram(@Valid @RequestBody TelegramAuthRequest request) {
        return authService.loginWithTelegram(request.initData());
    }

    @PostMapping("/auth/session")
    AuthResponse session(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SessionRequest request) {
        return authService.selectMembership(AuthenticatedUser.from(jwt), request.membershipId());
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(AuthenticatedUser.from(jwt));
    }
}
