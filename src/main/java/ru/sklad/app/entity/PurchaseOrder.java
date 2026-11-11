package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter
@Setter
public class PurchaseOrder {
    // ID 1 is the shared locked draft; higher IDs hold saved daily snapshots.
    @Id
    private Long id = 1L;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false)
    private String status = "Черновик";
    @Column(nullable = false)
    private boolean initialized;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();
}
