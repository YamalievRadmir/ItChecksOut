package ru.sklad.app.views;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Вход — Всё сходится!")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final ru.sklad.app.service.PermissionService permissions;
    private final LoginForm login = new LoginForm();

    public LoginView(ru.sklad.app.service.DemoShopService demo, org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration authentication, org.springframework.core.env.Environment environment, ru.sklad.app.service.PermissionService permissions) {
        this.permissions = permissions;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("Всё сходится!");
        i18n.getForm().setUsername("Логин");
        i18n.getForm().setPassword("Пароль");
        i18n.getForm().setSubmit("Войти");
        i18n.getErrorMessage().setTitle("Не удалось войти");
        i18n.getErrorMessage().setMessage("Проверьте логин и пароль и попробуйте снова.");
        login.setI18n(i18n);
        login.setAction("login");
        login.setForgotPasswordButtonVisible(false);
        var logo = new com.vaadin.flow.component.html.Image("favicon.png", "Логотип");
        logo.setHeight("100px"); logo.getStyle().set("object-fit", "contain");
        add(logo, login);
        if (environment.getProperty("app.demo-login.enabled", Boolean.class, false)) add(new com.vaadin.flow.component.button.Button("Попробовать в демо-режиме", event -> {
            try {
                demo.prepare();
                var auth = authentication.getAuthenticationManager().authenticate(
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken.unauthenticated("demo", "demo"));
                var request = ((com.vaadin.flow.server.VaadinServletRequest) com.vaadin.flow.server.VaadinRequest.getCurrent()).getHttpServletRequest();
                var response = ((com.vaadin.flow.server.VaadinServletResponse) com.vaadin.flow.server.VaadinResponse.getCurrent()).getHttpServletResponse();
                var httpSession = request.getSession(true);
                java.util.Collections.list(httpSession.getAttributeNames()).forEach(httpSession::removeAttribute);
                request.changeSessionId();
                var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                new org.springframework.security.web.context.HttpSessionSecurityContextRepository().saveContext(context, request, response);
                getUI().ifPresent(ui -> ui.getPage().setLocation("/products"));
            } catch (Exception ex) { com.vaadin.flow.component.notification.Notification.show("Не удалось открыть демо-магазин"); }
        }));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getUI().getPage().executeJs("document.documentElement.setAttribute('theme', 'corporate')");
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (event.getLocation().getQueryParameters().getParameters().containsKey("success")
                && auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            String destination = permissions.landingRoute();
            event.getUI().getPage().executeJs("window.history.replaceState(null, '', $0)", destination);
            event.getUI().getPage().reload();
            return;
        }
        login.setError(event.getLocation().getQueryParameters()
                .getParameters().containsKey("error"));
    }
}
