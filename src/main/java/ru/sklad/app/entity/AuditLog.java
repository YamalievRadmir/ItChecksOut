package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(indexes = @Index(name = "idx_audit_timestamp", columnList = "timestamp"))
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    private String username;
    private String action;
    private String entityType;
    @Column(columnDefinition = "TEXT")
    private String entityName;
    @Column(columnDefinition = "TEXT")
    private String details;
}
