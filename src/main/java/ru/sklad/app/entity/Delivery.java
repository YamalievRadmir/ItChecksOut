package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Delivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;
    @Column(nullable = false)
    private LocalDate deliveryDate;
    @Column(nullable = false)
    private LocalTime deliveryTime;
    @Column(nullable = false)
    private String paymentMethod;
    @Column(columnDefinition = "TEXT")
    private String comment;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalSum;
    @Column(nullable = false)
    private String status = "Новая";
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryItem> items = new ArrayList<>();
}
