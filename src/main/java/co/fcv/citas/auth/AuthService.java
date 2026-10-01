package co.fcv.citas.auth;

import co.fcv.citas.insurance.InsurancePlan;
import co.fcv.citas.insurance.InsurancePlanRepository;
import co.fcv.citas.insurance.UserInsuranceAffiliation;
import co.fcv.citas.insurance.UserInsuranceAffiliationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static co.fcv.citas.auth.AuthDtos.*;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserAccountRepository users; private final RoleRepository roles; private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder; private final JwtService jwt;
    private final InsurancePlanRepository plans; private final UserInsuranceAffiliationRepository affiliations;
    private final PasswordResetTokenRepository passwordResetTokens;
    public AuthService(UserAccountRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, JwtService jwt,
                       InsurancePlanRepository plans, UserInsuranceAffiliationRepository affiliations, PasswordResetTokenRepository passwordResetTokens) {
        this.users = users; this.roles = roles; this.refreshTokens = refreshTokens; this.passwordEncoder = passwordEncoder; this.jwt = jwt;
        this.plans = plans; this.affiliations = affiliations; this.passwordResetTokens = passwordResetTokens;
    }
    @Transactional
    public RegisteredUser register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String type = request.documentType().trim().toUpperCase(); String number = request.documentNumber().trim();
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("email already registered");
        if (users.existsByDocumentTypeAndDocumentNumber(type, number)) throw new ConflictException("document already registered");
        Long requestedPlanId = request.insurancePlanId() != null ? request.insurancePlanId() : request.planId();
        InsurancePlan plan = requestedPlanId == null ? null : plans.findActiveByIdWithActiveEps(requestedPlanId).orElseThrow(InvalidPlanException::new);
        Role userRole = roles.findByCode("USER").orElseThrow(() -> new IllegalStateException("USER role is missing"));
        UserAccount user = new UserAccount(request.firstName().trim(), request.lastName().trim(), type, number, email, request.phone().trim(), passwordEncoder.encode(request.password()));
        user.addRole(userRole); users.save(user);
        if (plan != null) affiliations.save(new UserInsuranceAffiliation(user, plan));
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
    @Transactional
    public RequestPasswordResetResponse requestPasswordReset(RequestPasswordResetRequest request) {
        Optional<UserAccount> user = users.findByEmailIgnoreCase(request.email().trim()).filter(UserAccount::isActive);
        if (user.isEmpty()) {
            log.info("Password reset requested for an email with no active account");
            return new RequestPasswordResetResponse("Si el correo existe, se generó un token de recuperación.", null);
        }
        String token = UUID.randomUUID().toString();
        passwordResetTokens.save(new PasswordResetToken(user.get(), hash(token), Instant.now().plus(Duration.ofMinutes(15))));
        log.info("Password reset token issued for userId={} (development-only token surfaced in the API response)", user.get().getId());
        return new RequestPasswordResetResponse("Si el correo existe, se generó un token de recuperación.", token);
    }

    @Transactional
    public void resetPassword(ConfirmPasswordResetRequest request) {
        PasswordResetToken stored = passwordResetTokens.findByTokenHash(hash(request.token()))
                .filter(candidate -> candidate.isUsable(Instant.now())).orElseThrow(InvalidResetTokenException::new);
        stored.markUsed(Instant.now());
        stored.getUser().changePassword(passwordEncoder.encode(request.newPassword()));
    }

    public static class ConflictException extends RuntimeException { public ConflictException(String message) { super(message); } }
    public static class InvalidPlanException extends RuntimeException { }
    public static class InvalidCredentialsException extends RuntimeException { }
    public static class InvalidResetTokenException extends RuntimeException { }
}
