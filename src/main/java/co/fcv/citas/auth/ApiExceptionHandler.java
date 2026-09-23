package co.fcv.citas.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthService.ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict() { return Map.of("error", "conflict"); }
    @ExceptionHandler({AuthService.InvalidCredentialsException.class, JwtService.InvalidTokenException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> unauthorized() { return Map.of("error", "unauthorized"); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> validation() { return Map.of("error", "validation_failed"); }
}
