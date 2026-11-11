package ru.sklad.app.views;
import org.junit.jupiter.api.Test;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.service.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SalesColumnsTest {
    @Test void salesAndCatalogColumnsSupportVisibilityMenu() throws Exception {
        UI.setCurrent(new UI());
        try {
            var products=mock(ProductRepository.class);
            SalesView view=new SalesView(products,mock(SalesService.class),mock(PermissionService.class),mock(HeldCartService.class),mock(PurchaseOrderService.class),mock(ProductAlternativeService.class),new SessionCart());
            Field field=SalesView.class.getDeclaredField("grid"); field.setAccessible(true);
            Grid<?> grid=(Grid<?>)field.get(view);
            assertTrue(grid.getColumns().stream().allMatch(c -> c.getKey()!=null));
            assertEquals(grid.getColumns().size(),grid.getColumns().stream().map(c -> c.getKey()).distinct().count());
            assertNotNull(grid.getColumnByKey("additives"));
            Method catalog=SalesView.class.getDeclaredMethod("createProductCatalog",ProductRepository.class); catalog.setAccessible(true);
            assertDoesNotThrow(() -> catalog.invoke(view,products));
        } finally { UI.setCurrent(null); }
    }
}
