package com.zentrapay.repository;

import com.zentrapay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email (case-sensitive)*
     * SQL: SELECT * FROM users WHERE email = ?
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by email (case-insensitive)*
     * Emails should be case-insensitive.
     * ammar@email.com = Ammar@email.com = AMMAR@EMAIL.COM*
     * Spring Data JPA handles the SQL:
     * SELECT * FROM users WHERE LOWER(email) = LOWER(?)
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Check if email exists*
     * SQL: SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
     */
    boolean existsByEmail(String email);
}