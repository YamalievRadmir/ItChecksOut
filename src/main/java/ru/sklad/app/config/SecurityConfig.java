package ru.sklad.app.config;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import ru.sklad.app.views.LoginView;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ru.sklad.app.service.PermissionService permissions) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                "POST".equals(request.getMethod()) && "/api/public/delivery-order".equals(request.getServletPath())));
        http.cors(org.springframework.security.config.Customizer.withDefaults());
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/public/delivery-order").permitAll();
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
                        permissions.canAccess(authentication.get(), route)));
            }
        });
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ManagedUserDetailsManager userDetailsService(PasswordEncoder passwordEncoder,
            javax.sql.DataSource dataSource, org.springframework.core.env.Environment environment) {
        var users = new ManagedUserDetailsManager(dataSource);
        for (String role : java.util.List.of("admin", "seller", "loader")) {
            if (users.userExists(role)) continue;
            String password = environment.getProperty("app.bootstrap." + role + "-password");
            if (password == null || password.isBlank()) {
                if (role.equals("admin")) throw new IllegalStateException("Set APP_BOOTSTRAP_ADMIN_PASSWORD for first startup");
                continue;
            }
            if (password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException("Bootstrap password must contain 12 characters minimum, 72 UTF-8 bytes maximum");
            users.createUser(User.withUsername(role).password(passwordEncoder.encode(password))
                    .roles(role.toUpperCase(java.util.Locale.ROOT)).build());
        }
        return users;
    }
}
