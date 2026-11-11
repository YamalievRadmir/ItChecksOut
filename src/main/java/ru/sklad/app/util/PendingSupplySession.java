package ru.sklad.app.util;

import com.vaadin.flow.server.VaadinSession;
import ru.sklad.app.service.PendingSupply;

/** Shared session key for the purchasing-to-receiving workflow. */
public final class PendingSupplySession {
    public static final String KEY = "pendingSupply";
    private PendingSupplySession() {}

    public static PendingSupply get(VaadinSession session) {
        Object value = session.getAttribute(KEY);
        return value instanceof PendingSupply pending ? pending : session.getAttribute(PendingSupply.class);
    }

    public static void put(VaadinSession session, PendingSupply pending) {
        session.setAttribute(KEY, pending);
        session.setAttribute(PendingSupply.class, null);
    }

    public static void clear(VaadinSession session) {
        session.setAttribute(KEY, null);
        session.setAttribute(PendingSupply.class, null);
    }
}
