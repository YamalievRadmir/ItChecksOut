package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="customer", uniqueConstraints=@UniqueConstraint(columnNames={"shop_id","phone"}))
@Getter
@Setter
public class Customer extends ShopOwned {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String address;
    @Column(nullable = false, unique = false)
    private String phone;
}
