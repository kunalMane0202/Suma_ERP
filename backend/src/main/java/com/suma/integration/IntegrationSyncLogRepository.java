package com.suma.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, UUID> {
    List<IntegrationSyncLog> findTop50ByIntegrationIdOrderByStartedAtDesc(UUID integrationId);
}
