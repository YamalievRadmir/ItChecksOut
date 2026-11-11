package ru.sklad.app.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Overrides any stale worker from a cached Vaadin development bundle. */
@RestController
@ConditionalOnProperty(name = "vaadin.productionMode", havingValue = "false", matchIfMissing = true)
public class DevelopmentServiceWorkerController {
    @GetMapping(value = "/sw.js", produces = "text/javascript")
    public ResponseEntity<Resource> retireWorker() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.valueOf("text/javascript"))
                .body(new ClassPathResource("META-INF/resources/sw.js"));
    }
}
