package ru.sklad.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.util.DiscountCalculator;
import java.math.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("@permissionService.canAccess('sales')")
public class HeldCartService {
    public record Item(Long productId, BigDecimal quantity) {}
    public record CartData(List<Item> items, BigDecimal discountValue, String discountType, String paymentMethod) {}
    public record RestoredLine(Product product, BigDecimal quantity) {}
    public record RestoredCart(List<RestoredLine> items, BigDecimal discountValue, String discountType, String paymentMethod) {}
    private final HeldCartRepository carts;
    private final ProductRepository products;
    private final ObjectMapper mapper;
    private final PermissionService permissions;
    private final AuditService audit;
    public HeldCartService(HeldCartRepository carts, ProductRepository products, ObjectMapper mapper, PermissionService permissions, AuditService audit) {
        this.carts = carts; this.products = products; this.mapper = mapper; this.permissions = permissions;
        this.audit = audit;
    }
    public long count() { return carts.count(); }
    public List<HeldCart> list() { return carts.findAllByOrderByCreatedAtDescIdDesc(); }
    public int positionCount(HeldCart cart) {
        try { return decode(cart).items().size(); } catch (IllegalArgumentException exception) { return 0; }
    }
    private CartData decode(HeldCart cart) {
        try {
            CartData data = mapper.readValue(cart.getCartData(), CartData.class);
            if (data == null || data.items() == null || data.items().isEmpty()) throw new IllegalArgumentException();
            return data;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Данные отложенного чека повреждены");
        }
    }
    private void validateQuantity(Long id, BigDecimal quantity) {
        if (id == null || quantity == null || quantity.signum() <= 0
                || quantity.compareTo(new BigDecimal("99999999.99")) > 0 || quantity.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Проверьте количество товаров в корзине");
    }
    private Product product(Long id) {
        Product product = products.findById(id).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует; отложенный чек сохранен"));
        if (product.getRetailPrice() == null || product.getRetailPrice().signum() < 0)
            throw new IllegalArgumentException("Проверьте розничную цену товара: " + product.getName());
        return product;
    }
    @Transactional
    public void hold(Map<Long, BigDecimal> quantities, BigDecimal discountValue, String discountType, String paymentMethod) {
        if (quantities == null || quantities.isEmpty()) throw new IllegalArgumentException("Корзина пуста");
        if (!Set.of("%", "руб.").contains(Objects.toString(discountType, ""))
                || !Set.of("Наличные", "Безнал").contains(Objects.toString(paymentMethod, "")))
            throw new IllegalArgumentException("Проверьте скидку и способ оплаты");
        if (discountValue != null && discountValue.signum() != 0 && !permissions.hasPermission("canGiveDiscount"))
            throw new IllegalArgumentException("У вас нет прав на предоставление скидки");
        List<Item> items = new ArrayList<>(); BigDecimal sum = BigDecimal.ZERO;
        for (var line : quantities.entrySet()) {
            validateQuantity(line.getKey(), line.getValue());
            sum = sum.add(product(line.getKey()).getRetailPrice().multiply(line.getValue()));
            items.add(new Item(line.getKey(), line.getValue()));
        }
        BigDecimal discount = discountValue == null ? BigDecimal.ZERO : discountValue;
        BigDecimal total = sum.subtract(DiscountCalculator.discount(sum, discount, discountType)).setScale(2, RoundingMode.HALF_UP);
        if (total.precision() > 20) throw new IllegalArgumentException("Сумма чека слишком велика");
        HeldCart cart = new HeldCart(); cart.setTotalSum(total);
        try { cart.setCartData(mapper.writeValueAsString(new CartData(items, discount, discountType, paymentMethod))); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Не удалось сохранить отложенный чек"); }
        carts.saveAndFlush(cart);
        audit.safeLogAction("СОЗДАНИЕ", "Отложенный чек", "#" + cart.getId(), "Товары: " + quantities + "; Итог: " + total);
    }
    @Transactional
    public RestoredCart restore(Long id) {
        HeldCart cart = carts.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Чек уже возвращен в корзину другим сотрудником"));
        CartData data = decode(cart);
        List<RestoredLine> lines = new ArrayList<>(); Set<Long> seen = new HashSet<>();
        for (Item item : data.items()) {
            if (item == null) throw new IllegalArgumentException("Данные отложенного чека повреждены");
            validateQuantity(item.productId(), item.quantity());
            if (!seen.add(item.productId())) throw new IllegalArgumentException("В отложенном чеке повторяются товары");
            lines.add(new RestoredLine(product(item.productId()), item.quantity()));
        }
        if (!Set.of("%", "руб.").contains(Objects.toString(data.discountType(), ""))
                || !Set.of("Наличные", "Безнал").contains(Objects.toString(data.paymentMethod(), ""))
                || data.discountValue() == null || data.discountValue().signum() < 0)
            throw new IllegalArgumentException("Данные отложенного чека повреждены");
        BigDecimal discount = permissions.hasPermission("canGiveDiscount") ? data.discountValue() : BigDecimal.ZERO;
        carts.delete(cart); carts.flush();
        audit.safeLogAction("УДАЛЕНИЕ", "Отложенный чек", "#" + id, "Возвращен в кассовую корзину");
        return new RestoredCart(List.copyOf(lines), discount, data.discountType(), data.paymentMethod());
    }
}
