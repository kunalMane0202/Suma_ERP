package com.suma.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmployeeCodeIgnoreCase(String code);
    List<User> findByStatus(String status);
    List<User> findByIdIn(Collection<UUID> ids);

    @Query("""
            select u from User u where (:q is null or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
              or lower(u.username) like lower(concat('%', cast(:q as string), '%'))
              or lower(u.employeeCode) like lower(concat('%', cast(:q as string), '%'))
              or lower(u.email) like lower(concat('%', cast(:q as string), '%')))
            and (:role is null or u.role.code = :role)
            and (:status is null or u.status = :status)
            and (:restrict = false or u.id in :ids)
            """)
    Page<User> search(@Param("q") String q, @Param("role") String role, @Param("status") String status,
                      @Param("restrict") boolean restrict, @Param("ids") Collection<UUID> ids, Pageable pageable);
}
