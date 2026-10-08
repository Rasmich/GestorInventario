package com.gestorinventario;

import com.gestorinventario.config.PasswordConfig;
import com.gestorinventario.config.SecurityConfig;
import com.gestorinventario.controller.*;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.UsuarioRepository;
import com.gestorinventario.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

// Contexto MVC con SecurityFilterChain real. No Spring Boot, servidor ni MySQL.
class JwtSecurityTest {
    private static final String CLAVE_TEST = Base64.getEncoder().encodeToString(
            "clave-ficticia-exclusiva-tests-32-bytes".getBytes(StandardCharsets.UTF_8));
    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private UsuarioRepository usuarios;
    private ProductoService productos;
    private JwtService jwt;
    private Usuario usuario;
    private String token;

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, PasswordConfig.class, AuthController.class, UsuarioController.class,
            ProductoController.class, GlobalExceptionHandler.class, PrincipalController.class})
    static class TestConfig {
        @Bean UsuarioRepository usuarioRepository() { return mock(UsuarioRepository.class); }
        @Bean ProductoService productoService() { return mock(ProductoService.class); }
        @Bean JwtService jwtService() {
            return new JwtService(CLAVE_TEST, 3600000, Clock.fixed(AHORA, ZoneOffset.UTC));
        }
        @Bean AuthService authService(UsuarioRepository repo, PasswordEncoder encoder, JwtService jwt) {
            return new AuthService(repo, encoder, jwt);
        }
        @Bean UsuarioService usuarioService(UsuarioRepository repo, PasswordEncoder encoder) {
            return new UsuarioService(repo, encoder);
        }
    }

    @RestController
    static class PrincipalController {
        // Solo existe en el contexto de pruebas para observar la autenticacion real.
        @GetMapping("/api/principal-test")
        public Map<String, Object> principal(Authentication authentication) {
            return Map.of("username", authentication.getName(),
                    "authorities", authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority()).toList());
        }
    }

    @BeforeEach
    void preparar() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuarios = context.getBean(UsuarioRepository.class);
        productos = context.getBean(ProductoService.class);
        jwt = context.getBean(JwtService.class);
        usuario = new Usuario("Ana", "ana",
                context.getBean(PasswordEncoder.class).encode("ClaveSegura123"), Rol.EMPLEADO);
        usuario.setId(1L);
        when(usuarios.findByUsername("ana")).thenReturn(Optional.of(usuario));
        when(productos.obtenerProductos()).thenReturn(List.of());
        token = jwt.generarToken(usuario);
    }

    @AfterEach
    void cerrar() {
        context.close();
    }

    @Test
    void loginPublicoSinTokenNiCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ana\",\"password\":\"ClaveSegura123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void creacionUsuarioAdminSinCsrf() throws Exception {
        usuario.setRol(Rol.ADMIN);
        when(usuarios.saveAndFlush(any())).thenAnswer(invocation -> {
            Usuario nuevo = invocation.getArgument(0);
            nuevo.setId(2L);
            return nuevo;
        });
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Bea\",\"username\":\"bea\",\"password\":\"ClaveSegura123\",\"rol\":\"EMPLEADO\"}"))
                .andExpect(status().isCreated());
        verify(usuarios).saveAndFlush(any());
    }

    @Test
    void productosSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
        verify(productos, never()).obtenerProductos();
    }

    @Test
    void productosConTokenValidoPermiteAcceso() throws Exception {
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(productos).obtenerProductos();
        verify(usuarios).findByUsername("ana");
    }

    @Test
    void tokenManipuladoDevuelve401() throws Exception {
        int inicioFirma = token.lastIndexOf('.') + 1;
        String alterado = token.substring(0, inicioFirma)
                + (token.charAt(inicioFirma) == 'A' ? 'B' : 'A') + token.substring(inicioFirma + 1);
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + alterado))
                .andExpect(status().isUnauthorized());
        verify(usuarios, never()).findByUsername(any());
    }

    @Test
    void tokenExpiradoDevuelve401() throws Exception {
        JwtService antiguo = new JwtService(CLAVE_TEST, 3600000,
                Clock.fixed(AHORA.minusSeconds(7200), ZoneOffset.UTC));
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + antiguo.generarToken(usuario)))
                .andExpect(status().isUnauthorized());
        verify(usuarios, never()).findByUsername(any());
    }

    @Test
    void usuarioInactivoDevuelve401() throws Exception {
        usuario.setActivo(false);
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verify(productos, never()).obtenerProductos();
    }

    @Test
    void usuarioInexistenteDevuelve401() throws Exception {
        when(usuarios.findByUsername("ana")).thenReturn(Optional.empty());
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorizationSinBearerDevuelve401() throws Exception {
        for (String header : new String[] {token, "Basic " + token, "Bearer", "Bearer ", "Bearer no-es-jwt"}) {
            mvc.perform(get("/api/productos").header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
        verify(usuarios, never()).findByUsername(any());
    }

    @Test
    void authoritiesUsanRolActualDeBaseDeDatos() throws Exception {
        usuario.setRol(Rol.ADMIN);
        String tokenAdmin = jwt.generarToken(usuario);
        usuario.setRol(Rol.EMPLEADO);
        String cuerpo = mvc.perform(get("/api/principal-test").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("ROLE_EMPLEADO"));
        assertFalse(cuerpo.contains("ROLE_ADMIN"));
        usuario.setRol(Rol.ADMIN);
        String actualizado = mvc.perform(get("/api/principal-test").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(actualizado.contains("ROLE_ADMIN"));
    }

    @Test
    void noCreaSesionNiCookiesNiConservaAutenticacionEntrePeticiones() throws Exception {
        var resultado = mvc.perform(get("/api/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertNull(resultado.getRequest().getSession(false));
        assertNull(resultado.getResponse().getHeader("Set-Cookie"));
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
    }

    @Test
    void restoDeApiYMetodosNoPublicosRequierenToken() throws Exception {
        for (String ruta : new String[] {"/api/categorias", "/api/proveedores",
                "/api/movimientos", "/api/usuarios", "/api/auth/login"}) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/movimientos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/productos/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginPublicoConCredencialesIncorrectasMantiene401() throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ana\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("Credenciales incorrectas"));
    }
}
