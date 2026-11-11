package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "supplies", uniqueConstraints = @UniqueConstraint(columnNames = {"receipt_key", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Supply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @Column(nullable = false)
    private LocalDate supplyDate;

    private String invoiceNumber;

    private String supplier;

    @Column(name = "receipt_key", length = 36)
    private String receiptKey;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal totalSum;
}
