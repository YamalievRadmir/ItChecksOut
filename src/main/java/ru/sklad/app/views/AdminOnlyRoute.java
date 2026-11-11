package ru.sklad.app.views;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import ru.sklad.app.util.RoleAccess;

public interface AdminOnlyRoute extends BeforeEnterObserver {
    @Override
    default void beforeEnter(BeforeEnterEvent event) {
        if (!RoleAccess.isAdmin()) event.rerouteTo(SalesView.class);
    }
}
