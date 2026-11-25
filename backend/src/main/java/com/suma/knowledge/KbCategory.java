package com.suma.knowledge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "kb_categories")
@Getter
@Setter
public class KbCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(name = "parent_id")
    private UUID parentId;
}
