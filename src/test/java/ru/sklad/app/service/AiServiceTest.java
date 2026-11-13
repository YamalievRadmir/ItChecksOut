package ru.sklad.app.service;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.data.domain.Sort;
import ru.sklad.app.entity.AnimalGuide;
import ru.sklad.app.repository.AnimalGuideRepository;
import ru.sklad.app.config.GlobalExceptionHandler;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class AiServiceTest {
    @Test void sendsEveryArticleAndUserQuestionWithoutStreaming() {
        var guides=mock(AnimalGuideRepository.class); var errors=mock(GlobalExceptionHandler.class);
        var a=new AnimalGuide(); a.setTitle("Куры"); a.setContent("Ракушка для несушек");
        var b=new AnimalGuide(); b.setFeedingAdvice("Кроликам сено"); b.setRecommendedProducts("ПК-1");
        when(guides.findAll(any(Sort.class))).thenReturn(List.of(a,b));
        var rest=new RestTemplate(); var server=MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://127.0.0.1:11434/api/generate")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.options.num_predict").value(1000))
            .andExpect(jsonPath("$.model").value("gemma3:12b")).andExpect(jsonPath("$.stream").value(false))
            .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("Ракушка для несушек"),org.hamcrest.Matchers.containsString("Кроликам сено"),
                org.hamcrest.Matchers.containsString("ПК-1"),org.hamcrest.Matchers.containsString("Чем кормить?"))))
            .andRespond(withSuccess("{\"response\":\"Ответ\",\"done\":true}",MediaType.APPLICATION_JSON));
        assertEquals("Ответ",new AiService(guides,rest,"http://127.0.0.1:11434/","gemma3:12b",errors).askAi("Чем кормить?"));
        server.verify(); verifyNoInteractions(errors);
    }
    @Test void emptyResponseIsLoggedAndConvertedToFriendlyError() {
        var guides=mock(AnimalGuideRepository.class); var errors=mock(GlobalExceptionHandler.class);
        var rest=new RestTemplate(); var server=MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://localhost:11434/api/generate")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertThrows(AiService.AiUnavailableException.class,()->new AiService(guides,rest,"http://localhost:11434","model",errors).askAi("Вопрос"));
        verify(errors).report(any(Throwable.class)); server.verify();
    }
    @Test void disconnectedOllamaReturnsMessageWithoutThrowing() {
        var guides=mock(AnimalGuideRepository.class); var errors=mock(GlobalExceptionHandler.class);
        var rest=mock(RestTemplate.class);
        when(rest.postForObject(anyString(),any(),eq(AiService.GenerateResponse.class)))
            .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));
        assertEquals(AiService.CONNECTION_UNAVAILABLE,new AiService(guides,rest,"http://localhost:11434","model",errors).askAi("Вопрос"));
        verify(errors).report(any(org.springframework.web.client.ResourceAccessException.class));
    }
    @Test void aiFailuresDoNotCreateVaadinNotifications() {
        var errors=mock(ru.sklad.app.service.ErrorLogService.class);
        var handler=new GlobalExceptionHandler(errors);
        var ui=new com.vaadin.flow.component.UI(); com.vaadin.flow.component.UI.setCurrent(ui);
        try {
            Throwable failure=new RuntimeException(new org.springframework.web.client.ResourceAccessException("Connection refused"));
            handler.notifyVaadin(failure);
            assertEquals(0,ui.getChildren().count());
            assertTrue(GlobalExceptionHandler.isAiFailure(failure));
            assertFalse(GlobalExceptionHandler.isAiFailure(new IllegalStateException("Database failure")));
            verifyNoInteractions(errors);
        } finally { com.vaadin.flow.component.UI.setCurrent(null); }
    }
    @Test void nestedAccessDeniedIsOnlyLoggedWithoutNotificationOrErrorRecord() {
        var errors=mock(ru.sklad.app.service.ErrorLogService.class);
        var handler=new GlobalExceptionHandler(errors);
        var ui=new com.vaadin.flow.component.UI(); com.vaadin.flow.component.UI.setCurrent(ui);
        try {
            var failure=new RuntimeException(new org.springframework.security.access.AccessDeniedException("Denied"));
            handler.notifyVaadin(failure);
            assertEquals(0,ui.getChildren().count());
            verifyNoInteractions(errors);
        } finally { java.lang.ref.Reference.reachabilityFence(ui); com.vaadin.flow.component.UI.setCurrent(null); }
    }    @Test void rejectsBlankQuestionWithoutDatabaseOrNetworkAccess() {
        var guides=mock(AnimalGuideRepository.class); var rest=mock(RestTemplate.class);
        var ai=new AiService(guides,rest,"http://localhost:11434","model",mock(GlobalExceptionHandler.class));
        assertThrows(IllegalArgumentException.class,()->ai.askAi("  ")); verifyNoInteractions(guides,rest);
    }
}
