package com.hf.easydelivery.operations.auth;

import com.hf.easydelivery.common.response.AppResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!memory")
@RequestMapping("/ops/auth")
public class OperationsAuthController {
    private final OperatorSessionService sessions;

    public OperationsAuthController(OperatorSessionService sessions) { this.sessions = sessions; }

    @PostMapping("/login")
    public AppResponse<?> login(@RequestBody LoginRequest request) {
        return AppResponse.success("Login successful", sessions.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public AppResponse<?> refresh(@RequestBody RefreshRequest request) {
        return AppResponse.success("Token refreshed", sessions.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public AppResponse<?> logout(HttpServletRequest request) {
        sessions.logout(bearer(request));
        return AppResponse.success("Logout successful", null);
    }

    @GetMapping("/me")
    public AppResponse<?> me(HttpServletRequest request) {
        return AppResponse.success(sessions.authenticate(bearer(request)));
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
    }

    public record LoginRequest(String username, String password) {}
    public record RefreshRequest(String refreshToken) {}
}

