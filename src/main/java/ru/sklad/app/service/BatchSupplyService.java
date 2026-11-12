package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.PurchaseOrderItem;
import ru.sklad.app.repository.PurchaseOrderRepository;
import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;
import ru.sklad.app.entity.PurchaseOrder;
import ru.sklad.app.repository.SupplyRepository;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BatchSupplyService {
    private final PurchaseOrderRepository orders;
    private final SupplyService supplies;
    private final AuditService audit;
    private final SupplyRepository history;
    public record PartyLine(Long productId, BigDecimal quantity, BigDecimal purchasePrice) {}

    public BatchSupplyService(PurchaseOrderRepository orders, SupplyService supplies, AuditService audit, SupplyRepository history) {
        this.orders = orders; this.supplies = supplies; this.audit = audit; this.history = history;
    }

    @Transactional
    @PreAuthorize("@permissionService.canAccess('supplies') and @permissionService.hasPermission('canMakeSupply')")
    public void receive(PendingSupply pending, LocalDate date, String invoice) {
        if (pending == null || pending.orderId() == null || pending.lines().isEmpty())
            throw new IllegalArgumentException("Нет товаров для пакетной приемки");
        receiveParty(UUID.randomUUID(), pending, pending.lines().stream()
                .map(line -> new PartyLine(line.productId(), line.quantity(), null)).toList(), date, invoice, null);
    }

    @Transactional
    @PreAuthorize("@permissionService.canAccess('supplies') and @permissionService.hasPermission('canMakeSupply')")
    public void receiveParty(UUID receiptId, PendingSupply origin, List<PartyLine> lines,
                             LocalDate date, String invoice, String supplier) {
        if (receiptId == null || lines == null || lines.isEmpty())
            throw new IllegalArgumentException("Добавьте товары в приемку");
        if (date == null || date.getYear() < 1 || date.getYear() > 9999)
            throw new IllegalArgumentException("Укажите корректную дату поставки");
        if ((invoice != null && invoice.trim().length() > 255) || (supplier != null && supplier.trim().length() > 255))
            throw new IllegalArgumentException("Номер накладной и поставщик должны содержать не более 255 символов");
        Set<Long> productIds = new HashSet<>();
        for (var line : lines) {
            if (line == null || line.productId() == null || !productIds.add(line.productId()))
                throw new IllegalArgumentException("В партии есть пустые или повторяющиеся товары");
        }
        String receiptKey = receiptId.toString();
        if (history.existsByReceiptKey(receiptKey)) throw new IllegalArgumentException("Эта партия уже оприходована");
        PurchaseOrder order = origin == null ? null : lockUnchangedOrder(origin);
        for (var line : lines.stream().sorted(Comparator.comparing(PartyLine::productId)).toList()) {
            supplies.receiveBatchLine(line, date, invoice, supplier, receiptKey);
        }
        if (order != null) {
            PurchaseOrderHistory.remember(orders, order);
            order.getItems().clear();
            orders.saveAndFlush(order);
            audit.safeLogAction("ИЗМЕНЕНИЕ", "Заявка на закупку", "#" + order.getId(),
                    "Партия оприходована, заявка очищена; позиций: " + lines.size() + "; Накладная: " + invoice);
        }
    }

    private PurchaseOrder lockUnchangedOrder(PendingSupply pending) {
        if (!PurchaseOrder.currentDraftId().equals(pending.orderId()))
            throw new IllegalArgumentException("Заказ недоступен в текущем магазине");
        var order = orders.findLockedById(pending.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Заявка больше не существует"));
        var current = order.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        // Item IDs also distinguish a newly filled draft from an already received order.
        boolean unchanged = current.size() == pending.lines().size()
                && pending.lines().stream().map(PendingSupply.Line::orderItemId).distinct().count() == current.size()
                && pending.lines().stream().allMatch(line -> {
                    var item = current.get(line.orderItemId());
                    return item != null && item.getProduct().getId().equals(line.productId())
                            && line.quantity() != null && item.getQuantity().compareTo(line.quantity()) == 0;
                });
        if (!unchanged) throw new IllegalArgumentException("Заказ изменен или уже оприходован. Передайте его из «К закупке» заново");
        return order;
    }
}
