package ru.sklad.app.entity;
import jakarta.persistence.*;
@MappedSuperclass
@EntityListeners(ru.sklad.app.util.ShopEntityListener.class)
public abstract class ShopOwned {
 @ManyToOne @JoinColumn(name="shop_id")
 private Shop shop;
 @Transient private Long originalShopId;
 public void rememberShop() { originalShopId = shop == null ? null : shop.getId(); }
 public Long getOriginalShopId() { return originalShopId; }
 public Shop getShop() { return shop; }
 public void setShop(Shop shop) { this.shop = shop; }
}
