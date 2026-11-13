package ru.sklad.app.config;

import com.vaadin.flow.spring.security.RequestUtil;
import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RequestUtil requestUtil) throws Exception {
        // Vaadin protects its own internal requests with a session CSRF token.
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/login")
                .ignoringRequestMatchers(requestUtil::isFrameworkInternalRequest))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(requestUtil::isFrameworkInternalRequest).permitAll()
                .requestMatchers("/login", "/VAADIN/**", "/frontend/**", "/images/**", "/favicon.ico", "/favicon.png").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login").permitAll());
        return http.build();
    }
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}