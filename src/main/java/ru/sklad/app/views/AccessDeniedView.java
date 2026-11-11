package ru.sklad.app.views;

import com.vaadin.flow.router.AccessDeniedException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.RouteAccessDeniedError;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;

@PermitAll
public class AccessDeniedView extends RouteAccessDeniedError {
    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<AccessDeniedException> parameter) {
        getElement().setText("У вас нет прав, обратитесь к администратору");
        return 403;
    }
}
