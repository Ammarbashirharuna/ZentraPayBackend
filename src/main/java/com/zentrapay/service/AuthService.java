package com.zentrapay.service;

import com.zentrapay.dto.auth.AuthResponse;
import com.zentrapay.dto.auth.LoginRequest;
import com.zentrapay.dto.auth.RegisterRequest;
import com.zentrapay.entity.User;
import com.zentrapay.exception.DuplicateResourceException;
import com.zentrapay.exception.EmailNotVerifiedException;
import com.zentrapay.exception.InvalidCredentialsException;
import com.zentrapay.repository.UserRepository;
import com.zentrapay.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication Service
 *
 * Handles:
 * - User registration
 * - User login
 * - JWT token generation
 *
 * Business Logic Layer:
 * - Contains all authentication rules
 * - Validates data
 * - Hashes passwords
 * - Generates tokens
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    // Dependencies injected by Spring (constructor injection)
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Register new user
     *
     * Process:
     * 1. Check email doesn't exist (prevent duplicates)
     * 2. Hash password with BCrypt
     * 3. Create user entity
     * 4. Save to database
     * 5. Generate JWT token
     * 6. Return response
     *
     * @param request Registration data (email, password, fullName)
     * @return AuthResponse with token and user info
     * @throws DuplicateResourceException if email already exists
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        // STEP 1: Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed - email exists: {}", request.getEmail());
            throw new DuplicateResourceException("Email already registered");
        }

        // STEP 2: Hash password (BCrypt with cost 12)
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // STEP 3: Create user entity
        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(hashedPassword);
        user.setFullName(request.getFullName().trim());
        user.setEmailVerified(false); // Will verify via email later

        // STEP 4: Save to database
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        // STEP 5: Generate JWT token
        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getEmail());

        // TODO: Send verification email (implement later)

        // STEP 6: Build and return response
        return buildAuthResponse(savedUser, token);
    }

    /**
     * Authenticate user (login)
     *
     * Process:
     * 1. Find user by email
     * 2. Verify password matches
     * 3. Check email is verified
     * 4. Generate JWT token
     * 5. Return response
     *
     * @param request Login credentials (email, password)
     * @return AuthResponse with token and user info
     * @throws InvalidCredentialsException if email/password wrong
     * @throws EmailNotVerifiedException if email not verified
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // STEP 1: Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: {}", request.getEmail());
                    return new InvalidCredentialsException("Invalid email or password");
                });

        // STEP 2: Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed - wrong password: {}", request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // STEP 3: Check email verified
        if (!user.getEmailVerified()) {
            log.warn("Login failed - email not verified: {}", request.getEmail());
            throw new EmailNotVerifiedException(
                    "Please verify your email before logging in"
            );
        }

        log.info("Login successful: {}", user.getId());

        // STEP 4: Generate token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());

        // STEP 5: Return response
        return buildAuthResponse(user, token);
    }

    /**
     * Helper: Build authentication response
     *
     * Creates AuthResponse with:
     * - JWT token
     * - Token type (Bearer)
     * - Expiration time
     * - User info (NO password hash!)
     *
     * @param user User entity
     * @param token JWT token
     * @return AuthResponse
     */
    private AuthResponse buildAuthResponse(User user, String token) {
        // Build user DTO (only safe fields)
        AuthResponse.UserDTO userDTO = AuthResponse.UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();

        // Build auth response
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresAt(jwtUtil.getExpirationTime())
                .user(userDTO)
                .build();
    }
}