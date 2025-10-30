package com.phegon.phegonbank.auth_users.repo;

import com.phegon.phegonbank.auth_users.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetCodeRepo extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findByCode(String code);
    void deleteByUserId(Long userId);
}
