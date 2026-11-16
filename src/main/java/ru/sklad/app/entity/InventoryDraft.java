package ru.sklad.app.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Getter @Setter
public class InventoryDraft extends ShopOwned {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private java.time.LocalDate date;
 @Column(nullable=false) private String employeeName;
 @Column(nullable=false, columnDefinition="TEXT") private String itemsData;
}
