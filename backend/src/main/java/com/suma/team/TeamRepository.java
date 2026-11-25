package com.suma.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    boolean existsByNameIgnoreCase(String name);
    List<Team> findByProjectId(UUID projectId);
    List<Team> findByTeamLeaderId(UUID leaderId);

    @Query("select t from Team t where :userId member of t.memberIds")
    List<Team> findByMember(UUID userId);
}
