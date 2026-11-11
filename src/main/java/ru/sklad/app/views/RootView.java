package ru.sklad.app.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.PageTitle;
import jakarta.annotation.security.PermitAll;

@Route("")
@PageTitle("Всё сходится!")
@PermitAll
public class RootView extends Div implements BeforeEnterObserver {

    private final ru.sklad.app.service.PermissionService permissions;
    public RootView(ru.sklad.app.service.PermissionService permissions) { this.permissions = permissions; }
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.forwardTo(permissions.landingRoute());
    }
}
