package ru.sklad.app.service;

import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DemoDataService {
    public static final String RUN_ID = "price-2026-11-09-v1";
    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private final ProductRepository products;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SupplyRepository supplies;
    private final PurchaseOrderRepository orders;
    private final DeliveryRepository deliveries;
    private final HeldCartRepository heldCarts;
    private final DemoDataRunRepository runs;
    private final ProductPriceImporter importer;
    private final DemoHistoryGenerator generator;
    private final AuditService audit;

    public DemoDataService(ProductRepository products, SaleRepository sales, SaleItemRepository saleItems,
                           SupplyRepository supplies, PurchaseOrderRepository orders, DeliveryRepository deliveries,
                           HeldCartRepository heldCarts, DemoDataRunRepository runs, ProductPriceImporter importer,
                           DemoHistoryGenerator generator, AuditService audit) {
        this.products = products; this.sales = sales; this.saleItems = saleItems; this.supplies = supplies;
        this.orders = orders; this.deliveries = deliveries; this.heldCarts = heldCarts; this.runs = runs;
        this.importer = importer; this.generator = generator; this.audit = audit;
    }

    @Transactional(rollbackFor = Exception.class)
    public void initialize(Path priceFile, boolean resetRequested, boolean generateHistory) throws IOException {
        if (runs.existsById(RUN_ID)) { log.info("Набор {} уже импортирован; повторная очистка и генерация пропущены", RUN_ID); return; }
        if (!resetRequested && (products.count() > 0 || sales.count() > 0)) {
            log.info("Существующие данные сохранены. Для разового обновления нужен app.demo-data.reset=true"); return;
        }
        // Validate the complete file and every delivery mapping before deleting any rows.
        var catalog = importer.read(priceFile);
        List<DeliveryCopy> preserved = new ArrayList<>();
        if (resetRequested) {
            for (Delivery delivery : deliveries.findAllByShop(ru.sklad.app.util.SecurityUtils.businessShop())) {
                List<DeliveryLine> lines = delivery.getItems().stream().map(item -> new DeliveryLine(
                        match(item.getProduct().getName(), catalog.products()), item.getQuantity())).toList();
                preserved.add(new DeliveryCopy(delivery, lines));
            }
            preserved.forEach(copy -> copy.delivery().getItems().clear()); deliveries.flush();
            heldCarts.deleteAll(); heldCarts.flush(); // Stored carts refer to the old product IDs.
            saleItems.deleteAll(); saleItems.flush();
            sales.deleteAll(); sales.flush();
            supplies.deleteAll(); supplies.flush();
            orders.deleteAll(); orders.flush(); // Cascades to PurchaseOrderItem, including saved snapshots.
            products.deleteAll(); products.flush();
        }
        products.saveAll(catalog.products()); products.flush();
        for (DeliveryCopy copy : preserved) {
            for (DeliveryLine line : copy.lines()) {
                DeliveryItem item = new DeliveryItem(); item.setDelivery(copy.delivery());
                item.setProduct(line.product()); item.setQuantity(line.quantity()); copy.delivery().getItems().add(item);
            }
            deliveries.save(copy.delivery());
        }
        deliveries.flush();
        if (!orders.existsById(PurchaseOrder.currentDraftId())) orders.saveAndFlush(new PurchaseOrder());
        DemoHistoryGenerator.Result result = generateHistory && sales.count() == 0
                ? generator.generate(catalog.products(), LocalDateTime.now(), new Random(20261109L))
                : new DemoHistoryGenerator.Result(0, 0, 0, 0);
        DemoDataRun run = new DemoDataRun(); run.setId(RUN_ID); run.setCompletedAt(LocalDateTime.now());
        run.setSourceSha256(catalog.sha256()); run.setProducts(catalog.products().size()); run.setSales(result.sales());
        run.setSupplies(result.supplies()); run.setEmployees(result.employees()); run.setShifts(result.shifts()); runs.saveAndFlush(run);
        audit.safeLogAction("СОЗДАНИЕ", "Тестовые данные", RUN_ID, "Прайс SHA-256: " + catalog.sha256()
                + "; Товаров: " + run.getProducts() + "; Продаж: " + result.sales() + "; Поставок: " + result.supplies()
                + "; Тестовых сотрудников: " + result.employees() + "; Смен: " + result.shifts()
                + "; Сохранено доставок: " + preserved.size());
        log.info("DEMO DATA READY: products={}, sales={}, supplies={}, employees={}, shifts={}, preservedDeliveries={}",
                run.getProducts(), result.sales(), result.supplies(), result.employees(), result.shifts(), preserved.size());
    }

    private record DeliveryLine(Product product, BigDecimal quantity) {}
    private record DeliveryCopy(Delivery delivery, List<DeliveryLine> lines) {}
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\d]", "");
    }
    private static Product match(String oldName, List<Product> imported) {
        String normalized = key(oldName);
        // The existing delivery uses the previous long spelling of this SKU.
        if (normalized.equals(key("ПК-1 для кур-несушек — Глазов, 25 кг (ГОСТ)"))) normalized = key("ПК-1 (Глазов) 25 кг");
        String target = normalized;
        var candidates = imported.stream().filter(product -> key(product.getName()).equals(target)).toList();
        if (candidates.size() != 1) throw new IllegalArgumentException("Нельзя сохранить доставку: сопоставьте товар старого прайса «" + oldName + "» с новым прайсом до очистки");
        return candidates.get(0);
    }
}
