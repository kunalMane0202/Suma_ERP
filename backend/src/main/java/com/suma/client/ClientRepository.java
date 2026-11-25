package com.suma.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    long countByStatus(String status);

    @Query("""
            select c from Client c where (:q is null or lower(c.name) like lower(concat('%', cast(:q as string), '%'))
               or lower(c.code) like lower(concat('%', cast(:q as string), '%')))
            and (:status is null or c.status = :status)
            and (:restrict = false or c.id in :ids)
            """)
    Page<Client> search(@Param("q") String q, @Param("status") String status, @Param("restrict") boolean restrict,
                        @Param("ids") Collection<UUID> ids, Pageable pageable);
}
