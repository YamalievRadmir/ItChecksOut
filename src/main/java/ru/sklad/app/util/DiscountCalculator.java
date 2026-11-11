package ru.sklad.app.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DiscountCalculator {
    private DiscountCalculator() {}

    public static BigDecimal discount(BigDecimal subtotal, BigDecimal value, String type) {
        if (subtotal == null || subtotal.signum() < 0 || value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Скидка должна быть не меньше нуля");
        }
        if (!"%".equals(type) && !"руб.".equals(type)) {
            throw new IllegalArgumentException("Выберите тип скидки");
        }
        if ("%".equals(type) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Скидка не может превышать 100%");
        }
        BigDecimal discount = "%".equals(type) ? subtotal.multiply(value).movePointLeft(2) : value;
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Скидка не может превышать сумму корзины");
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }
}
