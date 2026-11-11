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

    private final LoginForm login = new LoginForm();

    public LoginView() {
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
        add(login);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        login.setError(event.getLocation().getQueryParameters()
                .getParameters().containsKey("error"));
    }
}
