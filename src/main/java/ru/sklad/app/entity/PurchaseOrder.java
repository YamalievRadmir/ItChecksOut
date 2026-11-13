package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter
@Setter
public class PurchaseOrder extends ShopOwned {
    // Each shop has a deterministic draft ID; snapshots use independent IDs.
    @Id
    private Long id = currentDraftId();
    public static Long currentDraftId() { long shop = ru.sklad.app.util.SecurityUtils.businessShop().getId(); return shop == 1L ? 1L : -shop; }
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false)
    private String status = "Черновик";
    @Column(nullable = false)
    private boolean initialized;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();
}
