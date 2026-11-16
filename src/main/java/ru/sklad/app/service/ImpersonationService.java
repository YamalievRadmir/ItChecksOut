package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.component.UI;

@Service
public class ImpersonationService {
 public static final String ORIGINAL_USER="ORIGINAL_USER";
 private static final String ORIGINAL_PAGE="ORIGINAL_USER_PAGE";
 private final ru.sklad.app.util.SecurityUtils security;
 private final ru.sklad.app.repository.UserRepository users;
 private final org.springframework.security.core.userdetails.UserDetailsService details;
 public ImpersonationService(ru.sklad.app.util.SecurityUtils security, ru.sklad.app.repository.UserRepository users, org.springframework.security.core.userdetails.UserDetailsService details) {
  this.security=security; this.users=users; this.details=details;
 }
 private jakarta.servlet.http.HttpSession session() {
  return ((VaadinServletRequest)VaadinRequest.getCurrent()).getHttpServletRequest().getSession();
 }
 public boolean active() { return session().getAttribute(ORIGINAL_USER) instanceof Authentication; }
 @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
 public void enter(String username) {
  var original=SecurityContextHolder.getContext().getAuthentication();
  var actor=security.getCurrentUser();
  if(actor==null || !("SUPER_ADMIN".equals(actor.getRole()) || "ADMIN".equals(actor.getRole()))) throw new AccessDeniedException("Нет прав для входа от имени пользователя");
  if(active()) throw new IllegalArgumentException("Сначала вернитесь в свой аккаунт");
  var target=users.findForSecurityByUsername(username).orElseThrow(()->new IllegalArgumentException("Пользователь не найден"));
  if(actor.getUsername().equals(target.getUsername())) throw new IllegalArgumentException("Вы уже вошли в этот аккаунт");
  if(!"SUPER_ADMIN".equals(actor.getRole()) && (target.getShop()==null || actor.getShop()==null || !actor.getShop().getId().equals(target.getShop().getId()) || !"SELLER".equals(target.getRole())))
   throw new AccessDeniedException("Можно войти только как продавец своего магазина");
  var user=details.loadUserByUsername(target.getUsername());
  if(!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()) throw new IllegalArgumentException("Аккаунт недоступен");
  var auth=UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities());
  session().setAttribute(ORIGINAL_USER,original);
  session().setAttribute(ORIGINAL_PAGE,"SUPER_ADMIN".equals(actor.getRole()) ? "/super-admin?tab=users" : "/settings");
  install(auth);
  UI.getCurrent().getPage().setLocation("SUPER_ADMIN".equals(target.getRole()) ? "/super-admin" : "/sales");
 }
 public void restore() {
  var saved=session().getAttribute(ORIGINAL_USER);
  if(!(saved instanceof Authentication original)) throw new AccessDeniedException("Исходный аккаунт не найден");
  // Reload account status before restoring access, including changes made while impersonating.
  var user=details.loadUserByUsername(original.getName());
  if(!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()) throw new AccessDeniedException("Исходный аккаунт недоступен");
  String page=java.util.Objects.toString(session().getAttribute(ORIGINAL_PAGE),"/");
  install(UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities()));
  session().removeAttribute(ORIGINAL_USER); session().removeAttribute(ORIGINAL_PAGE);
  UI.getCurrent().getPage().setLocation(page);
 }
 private void install(Authentication auth) {
  var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
  SecurityContextHolder.setContext(context);
  session().setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
 }
 public com.vaadin.flow.component.button.Button button(String username) {
  return new com.vaadin.flow.component.button.Button("Войти как",event -> {
   try { enter(username); } catch(IllegalArgumentException | AccessDeniedException ex) { com.vaadin.flow.component.notification.Notification.show(ex.getMessage()); }
  });
 }
}
