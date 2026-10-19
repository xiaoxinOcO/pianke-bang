package com.campus.controller.internal;

import com.campus.repository.UserAccountRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/auth")
public class AuthInternalController {

    private final UserAccountRepository userAccountRepository;

    public AuthInternalController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @PostMapping("/validate-session")
    public Map<String, Object> validateSession(@RequestBody Map<String, String> body) {
        String username = body == null ? "" : String.valueOf(body.getOrDefault("username", "")).trim();
        String sessionId = body == null ? "" : String.valueOf(body.getOrDefault("sessionId", "")).trim();
        if (username.isBlank() || sessionId.isBlank()) {
            return Map.of("valid", false);
        }
        var userOpt = userAccountRepository.findByUsernameIgnoreCase(username)
                .or(() -> userAccountRepository.findByUsername(username));
        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            return Map.of("valid", false, "enabled", false);
        }
        var user = userOpt.get();
        // 任一端会话命中即可：避免网关旧包把 rider 归一成 web 后误判踢下线
        boolean valid = sessionMatches(user.getActiveSessionId(), sessionId)
                || sessionMatches(user.getActiveMpSessionId(), sessionId)
                || sessionMatches(user.getActiveRiderSessionId(), sessionId);
        return Map.of(
                "valid", valid,
                "enabled", true,
                "userId", user.getId()
        );
    }

    private static boolean sessionMatches(String active, String sessionId) {
        return active != null && !active.isBlank() && active.trim().equals(sessionId);
    }
}
