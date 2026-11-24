package com.example.helpdesk.auth;

import com.example.helpdesk.model.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @GetMapping("/me")
    CurrentUser me(@AuthenticationPrincipal HelpdeskPrincipal principal) {
        return new CurrentUser(principal.id(), principal.getUsername(), principal.role());
    }

    record CsrfResponse(String headerName, String token) {

        @Override
        public String toString() {
            return "CsrfResponse[headerName=" + headerName + "]";
        }
    }

    record CurrentUser(Long id, String email, UserRole role) {
    }
}
