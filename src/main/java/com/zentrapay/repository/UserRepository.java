package com.zentrapay.repository;

import com.zentrapay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Spring Data JPA automatically implements this!
    Optional<User> findByEmail(String email);

    // Check if email exists
    boolean existsByEmail(String email);
}