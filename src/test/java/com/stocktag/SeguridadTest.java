package com.stocktag;

import com.stocktag.model.Empresa;
import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.UsuarioRepository;
import com.stocktag.security.UsuarioAutenticado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las pruebas usan una base H2 en memoria para no tocar ./data. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stocktag-test;DB_CLOSE_DELAY=-1",
        "stocktag.admin.username=admin",
        "stocktag.admin.password=admin123"
})
@AutoConfigureMockMvc
class SeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminSinSesionRedirigeAlLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginDelAdminRedirigeAAdmin() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("admin123"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void empresaDesactivadaNoPuedeIniciarSesion() throws Exception {
        Empresa empresa = new Empresa("Ferretería Inactiva", "900000001-1");
        empresa.setActiva(false);
        empresaRepository.save(empresa);
        usuarioRepository.save(new Usuario("ferreteria", passwordEncoder.encode("clave123"),
                Rol.EMPRESA, empresa));

        mockMvc.perform(formLogin("/login").user("ferreteria").password("clave123"))
                .andExpect(redirectedUrl("/login?desactivada"));
    }

    @Test
    void empresaActivaEntraAlInventarioPeroNoAlAdmin() throws Exception {
        Empresa empresa = empresaRepository.save(new Empresa("Papelería Central", "900000002-2"));
        Usuario usuario = usuarioRepository.save(new Usuario("papeleria", passwordEncoder.encode("clave123"),
                Rol.EMPRESA, empresa));

        mockMvc.perform(formLogin("/login").user("papeleria").password("clave123"))
                .andExpect(redirectedUrl("/"));

        UsuarioAutenticado principal = new UsuarioAutenticado(usuario);
        mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Papelería Central")));
        mockMvc.perform(get("/admin").with(user(principal)))
                .andExpect(status().isForbidden());
    }

    /** Bootstrap e íconos se sirven desde el .jar (WebJars) y el login los necesita sin sesión. */
    @Test
    void estilosLocalesSeSirvenSinSesionYSinCdn() throws Exception {
        mockMvc.perform(get("/webjars/bootstrap/dist/css/bootstrap.min.css"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/webjars/bootstrap-icons/font/bootstrap-icons.min.css"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/webjars/bootstrap-icons/font/fonts/bootstrap-icons.woff2"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/webjars/bootstrap/5.3.8/dist/css/bootstrap.min.css")))
                .andExpect(content().string(containsString("/webjars/bootstrap-icons/1.13.1/font/bootstrap-icons.min.css")))
                .andExpect(content().string(not(containsString("cdn.jsdelivr.net"))));
    }
}
