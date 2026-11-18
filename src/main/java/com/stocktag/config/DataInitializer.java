package com.stocktag.config;

import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import com.stocktag.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el usuario ADMIN_PLATAFORMA inicial la primera vez que arranca la aplicación.
 * Las credenciales se leen de application.properties (stocktag.admin.*).
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${stocktag.admin.username}") String adminUsername,
                           @Value("${stocktag.admin.password}") String adminPassword) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRol(Rol.ADMIN_PLATAFORMA)) {
            return;
        }
        Usuario admin = new Usuario(adminUsername, passwordEncoder.encode(adminPassword),
                Rol.ADMIN_PLATAFORMA, null);
        usuarioRepository.save(admin);
        log.info("Usuario ADMIN_PLATAFORMA inicial creado: {}", adminUsername);
    }
}
