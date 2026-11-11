package ru.sklad.app.views;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import ru.sklad.app.entity.Product;
import java.math.*;
import java.util.*;

/** One checkout per HTTP session, shared by successive SalesView instances. */
@Component
@SessionScope
public class SessionCart {
    private final Map<Long, CartItem> items = new LinkedHashMap<>();
    private double discountValue;
    private String discountType = "%";
    private boolean discountApplied;
    private String paymentMethod = "Наличные";
    public Map<Long, CartItem> getItems() { return items; }
    public double getDiscountValue() { return discountValue; }
    public String getDiscountType() { return discountType; }
    public boolean isDiscountApplied() { return discountApplied; }
    public String getPaymentMethod() { return paymentMethod; }
    public void settings(double value, String type, boolean applied, String payment) {
        discountValue=value; discountType=type; discountApplied=applied;
        if(payment!=null) paymentMethod=payment;
    }
    public static class CartItem {
        final Product product;
        BigDecimal price;
        BigDecimal quantity = BigDecimal.ONE;
        boolean valid = true;
        CartItem(Product product) { this.product=product; this.price=product.getRetailPrice(); }
        BigDecimal sum() { return price.multiply(quantity).setScale(2,RoundingMode.HALF_UP); }
    }
}
