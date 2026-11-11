package ru.sklad.app.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public final class InsufficientStockException extends IllegalArgumentException {
    public record Shortage(Long productId, String name, BigDecimal available, BigDecimal missing) implements java.io.Serializable {
        public String message() {
            return "Недостаточно товара: " + name + ". Остаток: " + available.stripTrailingZeros().toPlainString().replace('.', ',');
        }
    }
    private final List<Shortage> shortages;
    public InsufficientStockException(List<Shortage> shortages) {
        super(shortages.stream().map(Shortage::message).collect(Collectors.joining("\n")));
        this.shortages = List.copyOf(shortages);
    }
    public List<Shortage> getShortages() { return shortages; }
}
