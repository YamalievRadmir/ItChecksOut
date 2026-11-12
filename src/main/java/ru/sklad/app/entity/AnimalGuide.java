package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "animal_guides")
@Getter
@Setter
@NoArgsConstructor
public class AnimalGuide extends ShopOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String species;
    private String category;
    private String title;
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String animalType;

    @Column(nullable = false)
    private String ageGroup;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String feedingAdvice;

    @Column(columnDefinition = "TEXT")
    private String recommendedProducts;
}
