package com.zentrapay.service;

import com.zentrapay.dto.auth.LoginRequest;
import com.zentrapay.dto.auth.RegisterRequest;
import com.zentrapay.entity.EmailVerificationToken;
import com.zentrapay.entity.User;
import com.zentrapay.exception.DuplicateResourceException;
import com.zentrapay.exception.EmailNotVerifiedException;
import com.zentrapay.exception.InvalidCredentialsException;
import com.zentrapay.repository.EmailVerificationTokenRepository;
import com.zentrapay.repository.UserRepository;
import com.zentrapay.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock EmailService emailService;

    @InjectMocks AuthService service;

    @BeforeEach
    void setUp() {
        lenient().when(jwtUtil.generateToken(any(UUID.class), any(String.class))).thenReturn("test-jwt-token");
        lenient().when(jwtUtil.getExpirationTime()).thenReturn(LocalDateTime.now().plusHours(24));
    }

    // ── Register ────────────────────────────────────────────────────────────

    @Test
    void registerCreatesNewUserSuccessfully() {
        RegisterRequest request = new RegisterRequest("test@example.com", "Passw0rd!", "Test User");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(userRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            User u = new User();
            u.setId(inv.getArgument(0));
            u.setEmail("test@example.com");
            u.setFullName("Test User");
            u.setPasswordHash("$2a$12$hashed");
            u.setEmailVerified(false);
            return Optional.of(u);
        });
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.register(request);

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        verify(emailService).sendVerificationEmail(eq("test@example.com"), eq("Test User"), anyString());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("taken@example.com", "Passw0rd!", "Taken")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerNormalizesEmailToLowercase() {
        RegisterRequest request = new RegisterRequest("Test@Example.COM", "Passw0rd!", "Test User");

        when(userRepository.existsByEmail("Test@Example.COM")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(userRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            User u = new User();
            u.setId(inv.getArgument(0));
            u.setEmail("test@example.com");
            u.setFullName("Test User");
            u.setPasswordHash("$2a$12$hashed");
            u.setEmailVerified(false);
            return Optional.of(u);
        });
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(request);

        verify(userRepository).existsByEmail("Test@Example.COM");
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("test@example.com")
                .passwordHash("$2a$12$hashed").fullName("Test User")
                .emailVerified(true).build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "$2a$12$hashed")).thenReturn(true);

        var response = service.login(new LoginRequest("test@example.com", "Passw0rd!"));

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("test@example.com")
                .passwordHash("$2a$12$hashed").fullName("Test User")
                .emailVerified(true).build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("test@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsNonexistentUser() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "Passw0rd!")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsUnverifiedEmail() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("test@example.com")
                .passwordHash("$2a$12$hashed").fullName("Test User")
                .emailVerified(false).build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "$2a$12$hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("test@example.com", "Passw0rd!")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    // ── Verify Email ────────────────────────────────────────────────────────

    @Test
    void verifyEmailMarksUserAsVerified() {
        User user = User.builder().id(UUID.randomUUID()).emailVerified(false).build();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken("valid-token-123");
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusHours(24));

        when(tokenRepository.findByToken("valid-token-123")).thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.verifyEmail("valid-token-123");

        assertThat(user.getEmailVerified()).isTrue();
        assertThat(token.getUsed()).isTrue();
    }

    @Test
    void verifyEmailRejectsInvalidToken() {
        when(tokenRepository.findByToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("invalid"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        User user = User.builder().id(UUID.randomUUID()).emailVerified(false).build();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken("expired-token");
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyEmail("expired-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyEmailRejectsAlreadyUsedToken() {
        User user = User.builder().id(UUID.randomUUID()).emailVerified(true).build();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken("used-token");
        token.setUser(user);
        token.setUsed(true);
        token.setExpiresAt(LocalDateTime.now().plusHours(24));

        when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyEmail("used-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already been used");
    }
}
