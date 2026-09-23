package co.fcv.citas.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() { }
    public record RegisterRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Size(max = 20) String documentType,
            @NotBlank @Size(max = 30) String documentNumber,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            Long insurancePlanId,
            Long planId) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    public record RefreshRequest(@NotBlank String refreshToken) { }
    public record RegisteredUser(Long id, String email, List<String> roles) { }
    public record TokenPair(String accessToken, String refreshToken, String tokenType) { }
}
