package uz.edubot.security.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.security.CurrentMember;
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
    AuthResponse session(@AuthenticationPrincipal CurrentMember member, @Valid @RequestBody SessionRequest request) {
        return authService.selectMembership(member, request.membershipId());
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal CurrentMember member) {
        return authService.me(member);
    }
}
