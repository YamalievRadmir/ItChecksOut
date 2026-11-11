package ru.sklad.app.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import java.time.Duration;
import java.util.Map;

@Service
public class TelegramService {
    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private final String token;
    private final String chatId;
    private final RestTemplate restTemplate;

    public TelegramService(@Value("${telegram.bot.token}") String token,
                           @Value("${telegram.chat.id}") String chatId) {
        this.token = token.trim();
        this.chatId = chatId.trim();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void printChatIdHint() {
        log.info("Чтобы узнать chat_id, напишите боту сообщение и перейдите по ссылке https://api.telegram.org/bot<ТОКЕН>/getUpdates");
        if (token.isBlank() || chatId.isBlank()) {
            log.info("Уведомления Telegram отключены: заполните telegram.bot.token и telegram.chat.id");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryCreated(DeliveryCreatedEvent event) {
        sendMessage(event.message());
    }

    public void sendMessage(String text) {
        try {
            if (token.isBlank() || chatId.isBlank()) {
                log.info("Сообщение Telegram пропущено: токен или chat_id не настроены");
                return;
            }
            var response = restTemplate.postForEntity("https://api.telegram.org/bot" + token + "/sendMessage",
                    Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML"), Map.class);
            if (response.getBody() == null || !Boolean.TRUE.equals(response.getBody().get("ok"))) {
                log.warn("Telegram не подтвердил отправку уведомления; доставка сохранена");
            }
        } catch (RestClientResponseException exception) {
            // Do not log the request URL or exception text: they can contain the bot token.
            log.warn("Не удалось отправить уведомление Telegram (HTTP {}); доставка сохранена", exception.getStatusCode().value());
        } catch (Exception exception) {
            log.warn("Не удалось отправить уведомление Telegram ({}). Проверьте сеть и настройки; доставка сохранена",
                    exception.getClass().getSimpleName());
        }
    }
}
