package ru.sklad.app.config;
import org.springframework.context.annotation.*;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
@Configuration
public class AiConfiguration {
    @Bean("ollamaRestTemplate")
    RestTemplate ollamaRestTemplate() {
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10)); factory.setReadTimeout(Duration.ofSeconds(180));
        return new RestTemplate(factory);
    }
    @Bean("aiWorkerPool")
    ThreadPoolTaskExecutor aiWorkerPool() {
        var executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(2); executor.setMaxPoolSize(2);
        executor.setTaskDecorator(task -> {
            var snapshot = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            snapshot.setAuthentication(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            return new org.springframework.security.concurrent.DelegatingSecurityContextRunnable(task, snapshot);
        });
        executor.setQueueCapacity(4); executor.setThreadNamePrefix("ollama-"); return executor;
    }
    @Bean("aiExecutor")
    AsyncTaskExecutor aiExecutor(@org.springframework.beans.factory.annotation.Qualifier("aiWorkerPool") ThreadPoolTaskExecutor pool) {
        return pool;
    }
}
