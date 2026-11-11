package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ProductRecommendationsInitializer {
    private final ProductRepository products;
    private final AuditService audit;
    private static final Pattern CODE = Pattern.compile("(?<![\\p{L}\\d])(пк|кк|дк)\\s*[-–—]?\\s*(\\d+(?:[.-]\\d+)*)(?!\\d)");
    public ProductRecommendationsInitializer(ProductRepository products, AuditService audit) {
        this.products = products; this.audit = audit;
    }
    static String normalize(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT).replace('ё','е').replace('\u00a0',' ').trim();
    }
    static String group(String name) {
        String text=normalize(name);
        var code=CODE.matcher(text);
        if(code.find()) return "code:"+code.group(1)+"-"+code.group(2);
        String[] words=text.replaceAll("[^\\p{L}\\d]+", " ").trim().split("\\s+");
        if(words.length<2) return "";
        return "words:"+String.join(" ", Arrays.copyOf(words,Math.min(3,words.length)));
    }
    static boolean additive(Product p) {
        String category=normalize(p.getCategory()), name=normalize(p.getName());
        return category.contains("добав") || category.contains("витамин") || category.contains("премикс")
                || name.matches(".*(?:ракушк|премикс|витамин).* ".trim()) || name.matches("мел(?:\\s.*)?");
    }
    static boolean feed(Product p) {
        String name=normalize(p.getName()), category=normalize(p.getCategory());
        if(additive(p) || category.contains("бакале") || category.contains("зернов")
                || name.matches("^(?:пшениц|овес|ячмен|рожь|кукуруз|просо|горох|отруб).*")) return false;
        return category.contains("корм") || CODE.matcher(name).find();
    }
    @Transactional
    public void initializeIfEmpty() {
        List<Product> all=products.findAllForUpdate();
        if(all.isEmpty() || all.stream().anyMatch(p -> !p.getAlternativeProducts().isEmpty())) {
            LoggerFactory.getLogger(getClass()).info("Автозаполнение рекомендаций пропущено: каталог пуст или альтернативы уже настроены");
            return;
        }
        Map<String,List<Product>> groups=new LinkedHashMap<>();
        for(Product p:all) {
            String key=group(p.getName());
            if(!key.isEmpty()) groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(p);
        }
        int links=0, additives=0;
        for(var peers:groups.values()) for(Product p:peers) for(Product other:peers)
            if(!p.getId().equals(other.getId()) && p.getAlternativeProducts().add(other)) links++;
        List<Product> supplements=all.stream().filter(ProductRecommendationsInitializer::additive).toList();
        for(Product p:all) if(feed(p)) for(Product other:supplements)
            if(!p.getId().equals(other.getId()) && p.getRecommendedAdditives().add(other)) additives++;
        products.flush();
        if(links>0 || additives>0) audit.logAction("ИЗМЕНЕНИЕ", "Каталог", "Автоподбор рекомендаций",
                "Связей альтернатив: "+links+"; рекомендаций добавок: "+additives);
        LoggerFactory.getLogger(getClass()).info("Автозаполнение рекомендаций: связей альтернатив {}, добавок {}",links,additives);
    }
}
