package ru.sklad.app.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.client.*;
import org.springframework.data.domain.Sort;
import ru.sklad.app.repository.AnimalGuideRepository;
import ru.sklad.app.config.GlobalExceptionHandler;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Service
public class AiService {
    public static final String CONNECTION_UNAVAILABLE = "ИИ-помощник временно недоступен (нет связи с локальным сервером Ollama). Попробуйте позже.";
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException() { super("ИИ сейчас недоступен. Проверьте Ollama и установленную модель или повторите позже."); }
    }
    public record GenerateRequest(String model, String prompt, boolean stream) {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record GenerateResponse(String response, String error) {}
    private final AnimalGuideRepository guides;
    private final RestTemplate rest;
    private final String endpoint;
    private final String model;
    private final GlobalExceptionHandler errors;
    public AiService(AnimalGuideRepository guides, @Qualifier("ollamaRestTemplate") RestTemplate rest,
                     @Value("${ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
                     @Value("${ollama.model:gemma3:12b}") String model, GlobalExceptionHandler errors) {
        this.guides=guides; this.rest=rest; this.endpoint=baseUrl.replaceAll("/+$","")+"/api/generate";
        this.model=model; this.errors=errors;
    }
    @PreAuthorize("isAuthenticated()")
    public String askAi(String prompt) {
        if(prompt==null || prompt.isBlank() || prompt.length()>4000)
            throw new IllegalArgumentException("Введите вопрос длиной до 4000 символов");
        StringBuilder context=new StringBuilder("Ты — консультант магазина кормов. Отвечай кратко и на русском языке. Вот база знаний:\n");
        // Repository transaction ends before the slow network call; no connection is held during inference.
        for(var article:guides.findAll(Sort.by("id"))) {
            context.append("\n--- Статья ---\n");
            for(String value:new String[]{article.getSpecies(),article.getCategory(),article.getTitle(),article.getAnimalType(),
                    article.getAgeGroup(),article.getContent(),article.getFeedingAdvice(),article.getRecommendedProducts()})
                if(value!=null && !value.isBlank()) context.append(value).append('\n');
        }
        context.append("\n--- Конец базы знаний ---\nВопрос пользователя:\n").append(prompt.trim());
        try {
            GenerateResponse result=rest.postForObject(endpoint,new GenerateRequest(model,context.toString(),false),GenerateResponse.class);
            if(result==null || (result.error()!=null && !result.error().isBlank()) || result.response()==null || result.response().isBlank())
                throw new RestClientException("Ollama returned an empty or invalid response");
            return result.response().trim();
        } catch(ResourceAccessException exception) {
            errors.report(exception);
            return CONNECTION_UNAVAILABLE;
        } catch(RestClientException exception) {
            errors.report(exception);
            throw new AiUnavailableException();
        }
    }
}
