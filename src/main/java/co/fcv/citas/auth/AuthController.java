package co.fcv.citas.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static co.fcv.citas.auth.AuthDtos.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public RegisteredUser register(@Valid @RequestBody RegisterRequest request) { return authService.register(request); }
    @PostMapping("/login")
    public TokenPair login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }
    @PostMapping("/refresh")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest request) { return authService.refresh(request); }
}
