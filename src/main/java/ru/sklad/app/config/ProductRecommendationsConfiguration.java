package ru.sklad.app.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import ru.sklad.app.service.ProductRecommendationsInitializer;
@Configuration
public class ProductRecommendationsConfiguration {
    @Bean @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    ApplicationRunner initializeProductRecommendations(ProductRecommendationsInitializer initializer) {
        return args -> initializer.initializeIfEmpty();
    }
}
