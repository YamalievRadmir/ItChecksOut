package ru.sklad.app.util;
import jakarta.persistence.*;
import ru.sklad.app.entity.*;
import org.springframework.security.access.AccessDeniedException;
public class ShopEntityListener {
 @PostLoad public void loaded(ShopOwned entity) { entity.rememberShop(); }
 @PrePersist @PreUpdate @PreRemove public void validate(ShopOwned entity) {
   Shop current = SecurityUtils.currentShop();
   if (entity.getOriginalShopId() != null && !entity.getOriginalShopId().equals(current.getId()))
       throw new AccessDeniedException("Cannot modify another shop's record");
   if (entity.getShop() == null) entity.setShop(current);
   check(entity, current);
   for (Class<?> type=org.hibernate.Hibernate.getClass(entity); type!=null && type!=ShopOwned.class; type=type.getSuperclass()) {
     for (var field:type.getDeclaredFields()) {
       if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
       if (!ShopOwned.class.isAssignableFrom(field.getType()) && !java.util.Collection.class.isAssignableFrom(field.getType())) continue;
       try {
         field.setAccessible(true); Object value=field.get(entity);
         if (value instanceof ShopOwned related) checkRelated(related,current);
         if (value instanceof java.util.Collection<?> items) for (Object item:items)
           if (item instanceof ShopOwned related) checkRelated(related,current);
       } catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
     }
   }
 }
 private void checkRelated(ShopOwned entity, Shop current) {
   if (entity.getShop()==null) entity.setShop(current);
   check(entity,current);
 }
 private void check(ShopOwned entity, Shop current) {
   if (entity.getShop()==null || !current.getId().equals(entity.getShop().getId()))
       throw new AccessDeniedException("Cross-shop relationship rejected");
 }
}