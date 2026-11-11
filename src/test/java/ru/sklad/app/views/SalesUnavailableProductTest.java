package ru.sklad.app.views;

import org.junit.jupiter.api.Test;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.service.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesUnavailableProductTest {
    private Product product(long id, String name, int stock) {
        Product p = new Product(); p.setId(id); p.setName(name);
        p.setStockQuantity(BigDecimal.valueOf(stock)); p.setRetailPrice(BigDecimal.TEN); return p;
    }
    private Object field(Object instance, String name) throws Exception {
        Field field=instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private Object call(Object instance,String name,Class<?>[] types,Object... args) throws Exception {
        Method method=instance.getClass().getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(instance,args);
    }
    @Test void unavailableItemStaysWhenNoReplacementExists() throws Exception {
        UI.setCurrent(new UI());
        try {
            var repository=mock(ProductRepository.class); var alternatives=mock(ProductAlternativeService.class);
            Product p=product(1,"Нет в наличии",0); when(repository.findById(1L)).thenReturn(Optional.of(p));
            var view=new SalesView(repository,mock(SalesService.class),mock(PermissionService.class),mock(HeldCartService.class),mock(PurchaseOrderService.class),alternatives,new SessionCart());
            assertEquals(true,call(view,"addProduct",new Class<?>[]{Product.class},p));
            Map<?,?> cart=(Map<?,?>)field(view,"cart"); assertEquals(1,cart.size());
            assertEquals(BigDecimal.ONE,field(cart.get(1L),"quantity"));
            assertNull(field(view,"alternativeDialog"));
        } finally { UI.setCurrent(null); }
    }
    @Test void sameReplacementDialogRetainsRequestOnCancelAndReplacesItOnConfirmation() throws Exception {
        UI.setCurrent(new UI());
        try {
            var repository=mock(ProductRepository.class); var alternatives=mock(ProductAlternativeService.class);
            Product source=product(1,"Нет в наличии",0), replacement=product(2,"Замена",5);
            when(repository.findById(1L)).thenReturn(Optional.of(source)); when(repository.findById(2L)).thenReturn(Optional.of(replacement));
            when(alternatives.find(eq(1L),any(),anyMap())).thenReturn(Optional.of(replacement));
            var view=new SalesView(repository,mock(SalesService.class),mock(PermissionService.class),mock(HeldCartService.class),mock(PurchaseOrderService.class),alternatives,new SessionCart());
            call(view,"addProduct",new Class<?>[]{Product.class},source);
            Dialog dialog=(Dialog)field(view,"alternativeDialog"); assertTrue(dialog.isOpened()); dialog.close();
            Map<?,?> cart=(Map<?,?>)field(view,"cart"); assertTrue(cart.containsKey(1L));
            assertEquals(true,call(view,"replaceShortage",new Class<?>[]{Long.class,Long.class,ProductRepository.class},1L,2L,repository));
            assertFalse(cart.containsKey(1L)); assertEquals(BigDecimal.ONE,field(cart.get(2L),"quantity"));
        } finally { UI.setCurrent(null); }
    }
}
