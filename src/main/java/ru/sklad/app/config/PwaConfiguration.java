package ru.sklad.app.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PwaConfiguration {
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> manifestMimeType() {
        return factory -> {
            MimeMappings mappings = new MimeMappings();
            mappings.add("webmanifest", "application/manifest+json");
            mappings.add("js", "text/javascript");
            mappings.add("mjs", "text/javascript");
            mappings.add("css", "text/css");
            // Preserve the embedded server's inherited MIME mappings.
            factory.addMimeMappings(mappings);
        };
    }
}
