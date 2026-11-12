package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale extends ShopOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime saleDate;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal totalSum;

    @Column(precision = 20, scale = 2)
    private BigDecimal discountSum = BigDecimal.ZERO;

    @Column(precision = 20, scale = 2)
    private BigDecimal finalSum;

    @Column(length = 16)
    private String paymentMethod = "CASH";

    public String getPaymentMethod() {
        return paymentMethod == null ? "CASH" : paymentMethod;
    }

    public BigDecimal getDiscountSum() {
        return discountSum == null ? BigDecimal.ZERO : discountSum;
    }

    public BigDecimal getFinalSum() {
        return finalSum == null ? totalSum : finalSum;
    }

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }
}
