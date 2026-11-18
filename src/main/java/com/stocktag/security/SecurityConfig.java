package com.stocktag.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN_PLATAFORMA")
                        .anyRequest().hasRole("EMPRESA"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(new RedireccionPorRolHandler())
                        .failureHandler((request, response, exception) -> {
                            String motivo = exception instanceof DisabledException ? "desactivada" : "error";
                            response.sendRedirect(request.getContextPath() + "/login?" + motivo);
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?salida")
                        .permitAll())
                // Registro de sesiones: no limita cuántas sesiones tiene un usuario (-1), solo las
                // registra para poder expirarlas al desactivar su empresa (CierreSesionesListener).
                // Una sesión expirada se cierra en su siguiente petición y va a /login?desactivada.
                .sessionManagement(sesion -> sesion
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?desactivada"));
        return http.build();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa al SessionRegistry cuando una sesión se cierra o vence, para no acumular sesiones muertas. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
