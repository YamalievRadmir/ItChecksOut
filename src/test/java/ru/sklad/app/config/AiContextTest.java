package ru.sklad.app.config;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class AiContextTest {
 @Test void tasksCaptureSubmittingUserAndDoNotLeakPreviousUser() throws Exception {
  var config=new AiConfiguration();var pool=config.aiWorkerPool();pool.initialize();var executor=config.aiExecutor(pool);
  try {
   var context=SecurityContextHolder.createEmptyContext();
   context.setAuthentication(new UsernamePasswordAuthenticationToken("first",""));SecurityContextHolder.setContext(context);
   var first=executor.submit(()->SecurityContextHolder.getContext().getAuthentication().getName());
   context.setAuthentication(new UsernamePasswordAuthenticationToken("second",""));
   var second=executor.submit(()->SecurityContextHolder.getContext().getAuthentication().getName());
   SecurityContextHolder.clearContext();
   var background=executor.submit(()->SecurityContextHolder.getContext().getAuthentication());
   assertEquals("first",first.get(5,TimeUnit.SECONDS));assertEquals("second",second.get(5,TimeUnit.SECONDS));assertNull(background.get(5,TimeUnit.SECONDS));
  } finally { SecurityContextHolder.clearContext();pool.shutdown(); }
 }
}