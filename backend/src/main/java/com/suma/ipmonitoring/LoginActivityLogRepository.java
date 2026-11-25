package com.suma.ipmonitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoginActivityLogRepository extends JpaRepository<LoginActivityLog, UUID>, JpaSpecificationExecutor<LoginActivityLog> {
    long countBySuccessAndCreatedAtAfter(boolean success, Instant after);
    long countByFailureCategoryAndCreatedAtAfter(String category, Instant after);
    long countBySuspiciousTrueAndCreatedAtAfter(Instant after);
    long countByIpAddressAndSuccessFalseAndCreatedAtAfter(String ip, Instant after);
    long countByUsernameAttemptedIgnoreCaseAndSuccessFalseAndCreatedAtAfter(String username, Instant after);
    List<LoginActivityLog> findTop5ByUserIdAndSuccessTrueOrderByCreatedAtDesc(UUID userId);

    @Query("select count(distinct l.ipAddress) from LoginActivityLog l where l.createdAt > :after")
    long countDistinctIps(Instant after);

    @Query("""
            select l.ipAddress as ip, count(l) as attempts, max(l.createdAt) as lastAttempt
            from LoginActivityLog l where l.success = false and l.createdAt > :after
            group by l.ipAddress having count(l) >= :threshold order by count(l) desc
            """)
    List<IpAttemptCount> repeatedFailures(Instant after, long threshold);

    @Modifying
    @Query("delete from LoginActivityLog l where l.createdAt < :before")
    int deleteOlderThan(Instant before);

    interface IpAttemptCount {
        String getIp();
        long getAttempts();
        Instant getLastAttempt();
    }
}
