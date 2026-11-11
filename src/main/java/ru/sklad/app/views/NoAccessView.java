package ru.sklad.app.views;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.PageTitle;
import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.spring.security.AuthenticationContext;

@Route(value = "no-access", layout = MainLayout.class)
@PageTitle("Нет доступа — Всё сходится!")
@PermitAll
public class NoAccessView extends VerticalLayout {
    public NoAccessView(AuthenticationContext authentication) {
        add(new Paragraph("У вас нет прав, обратитесь к администратору"),
                new Button("Выйти", e -> authentication.logout()));
    }
}
