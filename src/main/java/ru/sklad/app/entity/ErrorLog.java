package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(indexes = @Index(name = "idx_error_timestamp", columnList = "timestamp"))
public class ErrorLog {
    @ManyToOne(optional = true) @JoinColumn(name = "shop_id", nullable = true) private Shop shop;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    private String username;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
}
