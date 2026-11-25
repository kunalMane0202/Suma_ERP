package com.suma.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    List<UserSession> findByStatusAndExpiresAtBefore(String status, Instant before);
    Page<UserSession> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    List<UserSession> findByUserIdAndStatus(UUID userId, String status);
    long countByStatus(String status);

    @Query("select count(distinct s.userId) from UserSession s where s.status = 'ACTIVE'")
    long countDistinctActiveUsers();
}
