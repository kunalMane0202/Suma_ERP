package com.suma.ipmonitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrustedIpRuleRepository extends JpaRepository<TrustedIpRule, UUID> {
    List<TrustedIpRule> findByActiveTrue();
    boolean existsByCidrAndRuleType(String cidr, String ruleType);
}
