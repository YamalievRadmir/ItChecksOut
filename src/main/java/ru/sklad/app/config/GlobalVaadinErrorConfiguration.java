package ru.sklad.app.config;
import org.springframework.context.annotation.*;
import com.vaadin.flow.server.VaadinServiceInitListener;
@Configuration
public class GlobalVaadinErrorConfiguration {
    @Bean
    VaadinServiceInitListener globalVaadinErrorHandler(GlobalExceptionHandler handler) {
        return event -> event.getSource().addSessionInitListener(session ->
                session.getSession().setErrorHandler(error -> handler.notifyVaadin(error.getThrowable())));
    }
}
