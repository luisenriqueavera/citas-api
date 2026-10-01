package co.fcv.citas.config;

import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() { }
    public static Long id(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
