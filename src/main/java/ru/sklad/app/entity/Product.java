package ru.sklad.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @jakarta.persistence.ManyToMany
    @jakarta.persistence.JoinTable(name = "product_alternatives",
            joinColumns = @jakarta.persistence.JoinColumn(name = "product_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "alternative_id"))
    private java.util.Set<Product> alternativeProducts = new java.util.LinkedHashSet<>();

    @jakarta.persistence.ManyToMany
    @jakarta.persistence.JoinTable(name = "product_additives",
            joinColumns = @jakarta.persistence.JoinColumn(name = "product_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "additive_id"))
    private java.util.Set<Product> recommendedAdditives = new java.util.LinkedHashSet<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String barcode;

    private String category;

    private String direction;

    private String animalType;

    private String age;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String unit;

    @Column(precision = 10, scale = 2)
    private BigDecimal stockQuantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal retailPrice;
}
