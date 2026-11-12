package ru.sklad.app.config;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import ru.sklad.app.views.LoginView;








@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class SecurityConfig {

    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain siteOrders(HttpSecurity http,
            @org.springframework.beans.factory.annotation.Value("${app.site-order.api-key:}") String key) throws Exception {
        http.securityMatcher("/api/public/delivery-order");
        http.sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
        http.securityContext(context -> context.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository()));
        // This chain accepts an explicit integration secret, never browser session credentials.
        http.csrf(csrf -> csrf.disable());
        http.cors(org.springframework.security.config.Customizer.withDefaults());
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/api/public/delivery-order").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/public/delivery-order").access((ignored, request) -> {
                    String supplied = request.getRequest().getHeader("X-Site-Order-Key");
                    boolean valid = key.length() >= 32 && supplied != null && java.security.MessageDigest.isEqual(
                        key.getBytes(java.nio.charset.StandardCharsets.UTF_8), supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return new org.springframework.security.authorization.AuthorizationDecision(valid);
                }).anyRequest().denyAll());
        http.exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) -> response.sendError(401)));
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ru.sklad.app.service.PermissionService permissions) throws Exception {
        http.cors(org.springframework.security.config.Customizer.withDefaults());
        http.authorizeHttpRequests(authorize -> {
            // Match the management route before the shop-specific routes.
            authorize.requestMatchers("/super-admin", "/super-admin/**").hasRole("SUPER_ADMIN");
            authorize.requestMatchers("/login", "/login/**").permitAll();
            authorize.requestMatchers("/manifest.webmanifest", "/sw.js", "/offline.html",
                    "/icons/selkorm-192.png", "/icons/selkorm-512.png").permitAll();
            authorize.requestMatchers("/print-receipt/**").access((authentication, context) ->
                    new org.springframework.security.authorization.AuthorizationDecision(
                            permissions.canAccess(authentication.get(), "sales")
                                    || permissions.canAccess(authentication.get(), "sales-history")));
            for (String route : ru.sklad.app.service.PermissionService.VIEWS.keySet()) {
                authorize.requestMatchers("/" + route, "/" + route + "/**")
                    .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                        isShopUser(authentication.get()) && permissions.canAccess(authentication.get(), route)));
            }
        });
        http.sessionManagement(session -> session.sessionFixation(fixation -> fixation.newSession()));
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));
        return http.build();
    }

    private static boolean isShopUser(org.springframework.security.core.Authentication authentication) {
        var roles = authentication.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
        return !roles.contains("ROLE_SUPER_ADMIN") && (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SELLER"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
