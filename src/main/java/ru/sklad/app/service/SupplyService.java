package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.repository.SupplyRepository;
import ru.sklad.app.entity.Supply;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class SupplyService {

    private final ProductRepository repository;
    private final SupplyRepository supplies;
    private final AuditService audit;

    public SupplyService(ProductRepository repository, SupplyRepository supplies, AuditService audit) {
        this.repository = repository;
        this.supplies = supplies;
        this.audit = audit;
    }

    @Transactional
    @PreAuthorize("@permissionService.canAccess('supplies') and @permissionService.hasPermission('canMakeSupply')")
    public void receive(Long productId, BigDecimal quantity, LocalDate supplyDate, String invoiceNumber) {
        receiveInternal(productId, quantity, null, supplyDate, invoiceNumber, null, null);
    }

    @Transactional
    @PreAuthorize("@permissionService.canAccess('supplies') and @permissionService.hasPermission('canMakeSupply')")
    public void receiveBatchLine(BatchSupplyService.PartyLine line, LocalDate date, String invoice, String supplier, String receiptKey) {
        receiveInternal(line.productId(), line.quantity(), line.purchasePrice(), date, invoice, supplier, receiptKey);
    }

    private void receiveInternal(Long productId, BigDecimal quantity, BigDecimal offeredPrice, LocalDate supplyDate,
                                 String invoiceNumber, String supplier, String receiptKey) {
        if (quantity == null) {
            throw new IllegalArgumentException("Укажите количество товара");
        }
        if (productId == null || quantity.signum() <= 0 || supplyDate == null) {
            throw new IllegalArgumentException("Проверьте товар, количество и цену закупа");
        }
        quantity = quantity.setScale(2, RoundingMode.UNNECESSARY);
        if (quantity.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Количество превышает 99999999,99");
        Product product = repository.findLockedById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар больше не существует"));
        BigDecimal previousPrice = product.getPurchasePrice();
        BigDecimal purchasePrice = offeredPrice != null ? offeredPrice : previousPrice == null ? BigDecimal.ZERO : previousPrice;
        purchasePrice = purchasePrice.setScale(2, RoundingMode.UNNECESSARY);
        if (purchasePrice.signum() < 0 || purchasePrice.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("Исправьте закупочную цену в карточке товара");
        }
        BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
        if (stock.add(quantity).compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Остаток товара превышает допустимое значение: " + product.getName());
        product.setStockQuantity(stock.add(quantity));
        product.setPurchasePrice(purchasePrice);
        repository.save(product);
        Supply supply = new Supply();
        supply.setProduct(product);
        supply.setQuantity(quantity);
        supply.setPurchasePrice(purchasePrice);
        supply.setSupplyDate(supplyDate);
        supply.setInvoiceNumber(invoiceNumber == null ? null : invoiceNumber.trim());
        supply.setSupplier(supplier == null ? null : supplier.trim());
        supply.setReceiptKey(receiptKey);
        supply.setTotalSum(quantity.multiply(purchasePrice).setScale(2, RoundingMode.HALF_UP));
        supplies.save(supply);
        audit.logAction("СОЗДАНИЕ", "Поставка", product.getName(), "Остаток: " + stock + " → " + product.getStockQuantity()
                + "; Цена закупа: " + previousPrice + " → " + purchasePrice + "; Накладная: " + supply.getInvoiceNumber()
                + "; Поставщик: " + supply.getSupplier());
    }
}
