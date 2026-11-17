package ru.sklad.app.controller;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.repository.PublicCatalogRepository;
import ru.sklad.app.repository.ShopRepository;

@RestController
@RequestMapping("/api/public/catalog")
public class PublicCatalogController {
    private final PublicCatalogRepository catalog;
    private final ShopRepository shops;
    public PublicCatalogController(PublicCatalogRepository catalog, ShopRepository shops) {
        this.catalog = catalog;
        this.shops = shops;
    }
    public record CatalogItem(String name, String category, BigDecimal price,
                              BigDecimal stock, String unit, String description) {}

    @GetMapping("/{shopId}")
    @CrossOrigin(origins = "*")
    @Transactional(readOnly = true)
    public List<CatalogItem> catalog(@PathVariable Long shopId) {
        if (!shops.findById(shopId).map(shop -> shop.isActive()).orElse(false))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден");
        return catalog.catalog(shopId).stream().map(item -> new CatalogItem(item.getName(),
                item.getCategory(), item.getPrice(), item.getStock(), item.getUnit(), item.getDescription())).toList();
    }
}