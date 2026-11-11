package ru.sklad.app.views;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.notification.*;
import org.springframework.core.task.AsyncTaskExecutor;
import ru.sklad.app.service.SiteParserService;
import ru.sklad.app.config.GlobalExceptionHandler;
final class KnowledgeImportPanel extends VerticalLayout {
    KnowledgeImportPanel(SiteParserService parser,AsyncTaskExecutor executor,GlobalExceptionHandler errors) {
        Button load=new Button("Загрузить базу знаний с сайта");
        ProgressBar progress=new ProgressBar(); progress.setIndeterminate(true); progress.setVisible(false);
        Paragraph status=new Paragraph("Статьи появятся в справочнике животных и будут доступны ИИ. Существующие статьи с тем же заголовком сохраняются.");
        addClassName("app-card"); add(status,load,progress);
        load.addClickListener(event -> {
            UI ui=getUI().orElseThrow(); load.setEnabled(false); progress.setVisible(true);
            try { executor.execute(() -> {
                String message; boolean success;
                try { int count=parser.parseKnowledgeBase(); message="Загружено "+count+" статей"; success=true; }
                catch(Exception failure) { errors.report(failure); message="Не удалось загрузить статьи с сайта. Проверьте журнал ошибок и повторите позже."; success=false; }
                String result=message; boolean ok=success;
                try { ui.access(() -> {
                    if(!isAttached()) return;
                    load.setEnabled(true); progress.setVisible(false); status.setText(result);
                    Notification notification=Notification.show(result,6000,Notification.Position.MIDDLE);
                    notification.addThemeVariants(ok?NotificationVariant.LUMO_SUCCESS:NotificationVariant.LUMO_ERROR);
                }); } catch(com.vaadin.flow.component.UIDetachedException ignored) { }
            }); } catch(org.springframework.core.task.TaskRejectedException rejected) {
                load.setEnabled(true); progress.setVisible(false); Notification.show("Сервис занят. Повторите немного позже.");
            }
        });
    }
}
