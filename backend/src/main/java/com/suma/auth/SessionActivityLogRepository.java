package com.suma.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionActivityLogRepository extends JpaRepository<SessionActivityLog, UUID> {
    List<SessionActivityLog> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
