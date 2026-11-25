package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "break_categories")
@Getter
@Setter
public class BreakCategory {
    @Id
    @Column(length = 30)
    private String code;
    @Column(nullable = false, length = 80)
    private String label;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
