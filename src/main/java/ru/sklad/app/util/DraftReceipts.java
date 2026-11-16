package ru.sklad.app.util;

import java.math.BigDecimal;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.entity.Sale;
import ru.sklad.app.entity.SaleItem;

/** Immutable cart snapshots for printing only. Never persisted as sales. */
public final class DraftReceipts {
    private static final String KEY = DraftReceipts.class.getName();
    private DraftReceipts() {}
    public record Line(String name, String unit, BigDecimal quantity, BigDecimal price) implements java.io.Serializable {}
    private record Snapshot(String owner, List<Line> lines) implements java.io.Serializable {}
    public static Sale sale(List<Line> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Корзина пуста");
        Sale sale = new Sale(); sale.setSaleDate(java.time.LocalDateTime.now());
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) {
            if (line.quantity() == null || line.quantity().signum() <= 0 || line.price() == null || line.price().signum() < 0)
                throw new IllegalArgumentException("Проверьте количество и цену товаров в корзине");
            SaleItem item = new SaleItem(); item.setProductName(line.name()); item.setUnit(line.unit());
            item.setQuantity(line.quantity()); item.setRetailPrice(line.price());
            item.setTotalSum(line.quantity().multiply(line.price()).setScale(2, java.math.RoundingMode.HALF_UP));
            sale.addItem(item); total = total.add(item.getTotalSum());
        }
        sale.setTotalSum(total); sale.setFinalSum(total); return sale;
    }
    public static String store(HttpSession session, List<Line> lines) {
        sale(lines); // Validate before exposing the print URL.
        synchronized (session) {
            Map<String, Snapshot> snapshots = snapshots(session);
            String token = UUID.randomUUID().toString();
            snapshots.put(token, new Snapshot(SecurityContextHolder.getContext().getAuthentication().getName(), List.copyOf(lines)));
            while (snapshots.size() > 10) snapshots.remove(snapshots.keySet().iterator().next());
            session.setAttribute(KEY, snapshots);
            return token;
        }
    }
    public static List<Line> load(HttpSession session, String token) {
        if (session == null) return null;
        synchronized (session) {
            Snapshot snapshot = snapshots(session).get(token);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return snapshot != null && auth != null && snapshot.owner().equals(auth.getName()) ? snapshot.lines() : null;
        }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Snapshot> snapshots(HttpSession session) {
        Object stored = session.getAttribute(KEY);
        return stored instanceof Map ? (Map<String, Snapshot>)stored : new LinkedHashMap<>();
    }
}
