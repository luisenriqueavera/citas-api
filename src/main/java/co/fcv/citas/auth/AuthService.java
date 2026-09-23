package co.fcv.citas.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import static co.fcv.citas.auth.AuthDtos.*;

@Service
public class AuthService {
    private final UserAccountRepository users; private final RoleRepository roles; private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder; private final JwtService jwt;
    public AuthService(UserAccountRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users; this.roles = roles; this.refreshTokens = refreshTokens; this.passwordEncoder = passwordEncoder; this.jwt = jwt;
    }
    @Transactional
    public RegisteredUser register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String type = request.documentType().trim().toUpperCase(); String number = request.documentNumber().trim();
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("email already registered");
        if (users.existsByDocumentTypeAndDocumentNumber(type, number)) throw new ConflictException("document already registered");
        Role userRole = roles.findByCode("USER").orElseThrow(() -> new IllegalStateException("USER role is missing"));
        UserAccount user = new UserAccount(request.firstName().trim(), request.lastName().trim(), type, number, email, request.phone().trim(), passwordEncoder.encode(request.password()));
        user.addRole(userRole); users.save(user);
        return new RegisteredUser(user.getId(), user.getEmail(), List.of("USER"));
    }
    @Transactional
    public TokenPair login(LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim()).filter(UserAccount::isActive)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash())).orElseThrow(InvalidCredentialsException::new);
        return issueTokens(user);
    }
    @Transactional
    public TokenPair refresh(RefreshRequest request) {
        JwtService.Claims claims = jwt.validateRefreshToken(request.refreshToken());
        RefreshToken stored = refreshTokens.findByTokenHash(hash(request.refreshToken())).filter(token -> token.isUsable(Instant.now())).orElseThrow(JwtService.InvalidTokenException::new);
        stored.revoke(Instant.now());
        UserAccount user = users.findById(claims.subject()).filter(UserAccount::isActive).orElseThrow(JwtService.InvalidTokenException::new);
        return issueTokens(user);
    }
    private TokenPair issueTokens(UserAccount user) {
        String access = jwt.createAccessToken(user); String refresh = jwt.createRefreshToken(user);
        JwtService.Claims claims = jwt.validateRefreshToken(refresh);
        refreshTokens.save(new RefreshToken(user, hash(refresh), claims.expiresAt()));
        return new TokenPair(access, refresh, "Bearer");
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public static class ConflictException extends RuntimeException { public ConflictException(String message) { super(message); } }
    public static class InvalidCredentialsException extends RuntimeException { }
}
