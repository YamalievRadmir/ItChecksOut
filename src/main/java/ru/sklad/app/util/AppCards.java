package ru.sklad.app.util;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
/** A bounded card keeps table scrolling inside the table rather than growing the page. */
public final class AppCards {
    private AppCards() {}
    public static VerticalLayout table(Component table) {
        VerticalLayout card=new VerticalLayout(table);
        card.addClassNames("app-card","app-table-card"); card.setPadding(false); card.setSizeFull();
        card.setFlexGrow(1,table); return card;
    }
}
