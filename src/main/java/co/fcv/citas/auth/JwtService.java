package co.fcv.citas.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class JwtService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final String accessSecret;
    private final String refreshSecret;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtService(ObjectMapper objectMapper, @Value("${app.jwt.access-secret}") String accessSecret,
                      @Value("${app.jwt.refresh-secret}") String refreshSecret,
                      @Value("${app.jwt.access-minutes}") long accessMinutes,
                      @Value("${app.jwt.refresh-days}") long refreshDays) {
        this.objectMapper = objectMapper; this.accessSecret = accessSecret; this.refreshSecret = refreshSecret;
        this.accessMinutes = accessMinutes; this.refreshDays = refreshDays;
    }

    public String createAccessToken(UserAccount user) { return create(user, "access", Instant.now().plusSeconds(accessMinutes * 60), accessSecret); }
    public String createRefreshToken(UserAccount user) { return create(user, "refresh", Instant.now().plusSeconds(refreshDays * 86400), refreshSecret); }
    public Claims validateRefreshToken(String token) { return validate(token, "refresh", refreshSecret); }

    private String create(UserAccount user, String type, Instant expiresAt, String secret) {
        try {
            String header = encoded(Map.of("alg", "HS256", "typ", "JWT"));
            List<String> roles = user.getRoles().stream().map(Role::getCode).sorted().toList();
            String payload = encoded(Map.of("sub", user.getId(), "roles", roles, "typ", type, "jti", UUID.randomUUID().toString(), "iat", Instant.now().getEpochSecond(), "exp", expiresAt.getEpochSecond()));
            String signingInput = header + "." + payload;
            return signingInput + "." + sign(signingInput, secret);
        } catch (Exception ex) { throw new IllegalStateException("Unable to create token", ex); }
    }

    public record Claims(long subject, Instant expiresAt) { }
    private Claims validate(String token, String expectedType, String secret) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !MessageDigest.isEqual(DECODER.decode(parts[2]), DECODER.decode(sign(parts[0] + "." + parts[1], secret)))) throw new InvalidTokenException();
            Map<String, Object> payload = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() { });
            if (!expectedType.equals(payload.get("typ"))) throw new InvalidTokenException();
            long exp = ((Number) payload.get("exp")).longValue();
            if (!Instant.ofEpochSecond(exp).isAfter(Instant.now())) throw new InvalidTokenException();
            return new Claims(((Number) payload.get("sub")).longValue(), Instant.ofEpochSecond(exp));
        } catch (InvalidTokenException ex) { throw ex; }
        catch (Exception ex) { throw new InvalidTokenException(); }
    }
    private String encoded(Object value) throws Exception { return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value)); }
    private String sign(String input, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return ENCODER.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }
    public static class InvalidTokenException extends RuntimeException { }
}
