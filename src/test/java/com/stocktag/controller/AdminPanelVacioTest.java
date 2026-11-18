package com.stocktag.controller;

import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import com.stocktag.security.UsuarioAutenticado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Estado vacío del panel: base H2 propia, sin la empresa de ejemplo. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stocktag-vacio;DB_CLOSE_DELAY=-1",
        "stocktag.admin.username=admin",
        "stocktag.admin.password=admin123",
        "stocktag.datos-ejemplo=false"
})
@AutoConfigureMockMvc
class AdminPanelVacioTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinEmpresasMuestraElEstadoVacioConElBotonDeRegistrar() throws Exception {
        UsuarioAutenticado admin = new UsuarioAutenticado(new Usuario("admin", "x", Rol.ADMIN_PLATAFORMA, null));
        mockMvc.perform(get("/admin").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Todavía no hay empresas registradas"),
                        containsString("Registrar empresa"),
                        not(containsString("<table")))));
    }
}
