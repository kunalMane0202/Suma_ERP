package com.suma.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    List<Project> findByStatus(String status);
    List<Project> findByClientId(UUID clientId);
    long countByStatus(String status);

    @Query("""
            select p from Project p where (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
               or lower(p.code) like lower(concat('%', cast(:q as string), '%')))
            and (:clientId is null or p.clientId = :clientId)
            and (:status is null or p.status = :status)
            and (:restrict = false or p.id in :ids)
            """)
    Page<Project> search(@Param("q") String q, @Param("clientId") UUID clientId, @Param("status") String status,
                         @Param("restrict") boolean restrict, @Param("ids") Collection<UUID> ids, Pageable pageable);
}
