package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BreakCategoryRepository extends JpaRepository<BreakCategory, String> {
    List<BreakCategory> findAllByOrderBySortOrderAsc();
}
