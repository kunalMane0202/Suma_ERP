package com.suma.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequestEntity, java.util.UUID> {
    boolean existsByEmailIgnoreCaseAndStatus(String email, String status);
    List<RegistrationRequestEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);
}
