package ru.sklad.app.views;
import org.junit.jupiter.api.Test;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import ru.sklad.app.entity.Product;
import ru.sklad.app.entity.Sale;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SessionCartTest {
    Stream<Component> tree(Component c) { return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::tree)); }
    Button button(SalesView view,String text) { return tree(view).filter(Button.class::isInstance).map(Button.class::cast).filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
    SalesView view(SessionCart cart,ProductRepository products,SalesService sales) {
        return new SalesView(products,sales,mock(PermissionService.class),mock(HeldCartService.class),mock(PurchaseOrderService.class),mock(ProductAlternativeService.class),cart);
    }
    Product product() { Product p=new Product(); p.setId(1L); p.setName("Корм"); p.setStockQuantity(BigDecimal.TEN); p.setRetailPrice(BigDecimal.TEN); return p; }
    @Test void navigationPreservesCartAndSearchCloses() throws Exception {
        UI.setCurrent(new UI());
        try {
            SessionCart session=new SessionCart(); var products=mock(ProductRepository.class); var sales=mock(SalesService.class);
            Product p=product(); when(products.findById(1L)).thenReturn(Optional.of(p));
            SalesView first=view(session,products,sales);
            var field=SalesView.class.getDeclaredField("matches"); field.setAccessible(true); ComboBox<?> matches=(ComboBox<?>)field.get(first); matches.setOpened(true);
            var add=SalesView.class.getDeclaredMethod("addProduct",Product.class); add.setAccessible(true); add.invoke(first,p);
            assertFalse(matches.isOpened()); assertEquals(BigDecimal.ONE,session.getItems().get(1L).quantity);
            SalesView second=view(session,products,sales);
            assertEquals(1,session.getItems().size()); assertEquals(BigDecimal.ONE,session.getItems().get(1L).quantity);
            assertTrue(new SessionCart().getItems().isEmpty());
            button(second,"Очистить").click(); assertTrue(session.getItems().isEmpty());
        } finally { UI.setCurrent(null); }
    }
    @Test void paymentFailureKeepsCartAndSuccessClearsIt() {
        UI.setCurrent(new UI());
        try {
            SessionCart session=new SessionCart(); var products=mock(ProductRepository.class); var sales=mock(SalesService.class);
            Product p=product(); when(products.findById(1L)).thenReturn(Optional.of(p)); session.getItems().put(1L,new SessionCart.CartItem(p));
            when(sales.conductSale(anyMap(),anyMap(),any(),anyString(),anyString())).thenThrow(new IllegalArgumentException("Цена изменилась")).thenReturn(new Sale());
            var view=view(session,products,sales); button(view,"Оплатить").click(); assertEquals(1,session.getItems().size());
            button(view,"Оплатить").click(); assertTrue(session.getItems().isEmpty());
        } finally { UI.setCurrent(null); }
    }
}
