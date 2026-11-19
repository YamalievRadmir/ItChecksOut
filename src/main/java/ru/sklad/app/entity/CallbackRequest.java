package ru.sklad.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(indexes = @Index(name = "idx_callback_shop_pending", columnList = "shop_id, completed_at, created_at"))
@Getter
@Setter
public class CallbackRequest extends ShopOwned {
    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String customerName;
    @Column(nullable = false, length = 15)
    private String phone;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant completedAt;
    @Column(columnDefinition = "TEXT")
    private String orderDetails;
}
