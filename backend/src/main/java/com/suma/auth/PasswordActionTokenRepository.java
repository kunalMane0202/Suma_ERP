package com.suma.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordActionTokenRepository extends JpaRepository<PasswordActionToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordActionToken t where t.tokenHash = :tokenHash")
    Optional<PasswordActionToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update PasswordActionToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateUnused(@Param("userId") UUID userId, @Param("purpose") String purpose, @Param("now") Instant now);
}
