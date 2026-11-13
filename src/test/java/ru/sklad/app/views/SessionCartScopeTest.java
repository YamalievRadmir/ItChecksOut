package ru.sklad.app.views;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.scopes.VaadinSessionScope;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionCartScopeTest {
    @Test void resolvesCartWithoutHttpRequestAndSeparatesVaadinSessions() {
        RequestContextHolder.resetRequestAttributes();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.addBeanFactoryPostProcessor(new VaadinSessionScope());
            context.register(SessionCart.class); context.refresh();
            VaadinSession.setCurrent(session());
            var cart = context.getBean(SessionCart.class);
            cart.settings(15, "%", true, "Безнал");
            assertEquals(15, cart.getDiscountValue());
            assertNull(RequestContextHolder.getRequestAttributes());
            VaadinSession.setCurrent(session());
            assertEquals(0, context.getBean(SessionCart.class).getDiscountValue());
        } finally { VaadinSession.setCurrent(null); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private VaadinSession session() {
        var session = mock(VaadinSession.class);
        var attributes = new HashMap<Class<?>, Object>();
        when(session.getLockInstance()).thenReturn(new ReentrantLock());
        when(session.hasLock()).thenReturn(true);
        when(session.getState()).thenReturn(com.vaadin.flow.server.VaadinSessionState.OPEN);
        when(session.getAttribute(any(Class.class))).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> { attributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(session).setAttribute(any(Class.class), any());
        return session;
    }
}
