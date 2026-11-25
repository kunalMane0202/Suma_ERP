package com.suma.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientContactRepository extends JpaRepository<ClientContact, UUID> {
    List<ClientContact> findByClientIdOrderByPrimaryDescNameAsc(UUID clientId);
    void deleteByClientId(UUID clientId);
}
