package ru.sklad.app.views;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.Key;
import org.springframework.core.task.AsyncTaskExecutor;
import ru.sklad.app.service.AiService;
import ru.sklad.app.config.GlobalExceptionHandler;

final class AiChatDialog extends Dialog {
    private final VerticalLayout messages=new VerticalLayout();
    private final TextField input=new TextField();
    private final Button send=new Button("Отправить");
    private final ProgressBar loading=new ProgressBar();
    private java.util.concurrent.Future<?> pending;
    private long requestVersion;
    AiChatDialog(AiService ai, AsyncTaskExecutor executor, GlobalExceptionHandler errors) {
        setHeaderTitle("Консультант по кормам"); setWidth("650px"); setMaxWidth("95vw");
        messages.setHeight("360px"); messages.getStyle().set("overflow-y","auto");
        messages.getElement().setAttribute("aria-live","polite");
        input.setPlaceholder("Задайте вопрос о кормлении..."); input.setAriaLabel("Вопрос консультанту");
        input.setMaxLength(4000); input.setWidthFull();
        loading.setIndeterminate(true); loading.setVisible(false); loading.getElement().setAttribute("aria-label","ИИ готовит ответ");
        HorizontalLayout composer=new HorizontalLayout(input,send); composer.setWidthFull(); composer.setFlexGrow(1,input);
        VerticalLayout content=new VerticalLayout(messages,loading,composer); content.setPadding(false); add(content);
        getFooter().add(new Button("Закрыть",e -> close()));
        send.addClickShortcut(Key.ENTER).listenOn(input);
        send.addClickListener(event -> {
            String question=input.getValue().trim(); if(question.isEmpty() || !send.isEnabled()) return;
            UI ui=getUI().orElseThrow(); append("Вы",question); input.clear(); busy(true);
            long version=++requestVersion;
            try {
                pending=executor.submit(() -> {
                    String answer;
                    try { answer=ai.askAi(question); }
                    catch(AiService.AiUnavailableException | IllegalArgumentException expected) { answer=expected.getMessage(); }
                    catch(Exception failure) { errors.report(failure); answer="Не удалось получить ответ. Повторите позже."; }
                    String result=answer;
                    try { ui.access(() -> { if(isAttached() && version==requestVersion) { append("Консультант",result); busy(false); input.focus(); } }); }
                    catch(com.vaadin.flow.component.UIDetachedException ignored) { }
                });
            } catch(org.springframework.core.task.TaskRejectedException rejected) {
                append("Консультант","ИИ занят. Повторите вопрос немного позже."); busy(false);
            }
        });
        addDetachListener(event -> { ++requestVersion; if(pending!=null) pending.cancel(true); busy(false); });
    }
    private void busy(boolean value) { send.setEnabled(!value); input.setEnabled(!value); loading.setVisible(value); }
    private void append(String author,String text) {
        Div message=new Div(); message.setText(author+": "+text);
        message.getStyle().set("white-space","pre-wrap").set("overflow-wrap","anywhere")
                .set("padding","var(--lumo-space-s)").set("border-radius","var(--lumo-border-radius-m)")
                .set("background","var(--lumo-contrast-5pct)");
        messages.add(message); messages.getElement().executeJs("this.scrollTop=this.scrollHeight");
    }
}
