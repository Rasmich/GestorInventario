package com.gestorinventario;

import com.gestorinventario.config.PasswordConfig;
import com.gestorinventario.controller.GlobalExceptionHandler;
import com.gestorinventario.controller.UsuarioController;
import com.gestorinventario.model.Usuario;
import com.gestorinventario.repository.UsuarioRepository;
import com.gestorinventario.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sin Spring Boot ni base de datos: HTTP simulado y BCrypt real.
class UsuarioCreacionTest {
    private static final String PASSWORD = "ClaveSegura123";
    private static final String JSON_VALIDO =
            "{\"nombre\":\"Ana\",\"username\":\"ana\",\"password\":\"ClaveSegura123\",\"rol\":\"EMPLEADO\"}";
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                new UsuarioController(new UsuarioService(usuarios, encoder)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator).build();
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            assertNull(usuario.getId());
            usuario.setId(1L);
            return usuario;
        });
    }

    @AfterEach
    void cerrar() {
        validator.close();
    }

    @Test
    void creacionValidaGuardaBCryptYResponde201SinPasswordNiHash() throws Exception {
        String respuesta = mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captor.capture());
        Usuario usuario = captor.getValue();
        assertEquals("Ana", usuario.getNombre());
        assertEquals("ana", usuario.getUsername());
        assertTrue(usuario.getActivo());
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
        assertNotEquals(PASSWORD, usuario.getPassword());
        assertTrue(usuario.getPassword().startsWith("$2"));
        assertTrue(encoder.matches(PASSWORD, usuario.getPassword()));
        assertFalse(respuesta.contains("password"));
        assertFalse(respuesta.contains(usuario.getPassword()));
        assertFalse(respuesta.contains(PASSWORD));
        assertTrue(respuesta.contains("\"username\":\"ana\""));
    }

    @Test
    void passwordCortaDevuelve400SinGuardar() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO.replace(PASSWORD, "1234567"))).andExpect(status().isBadRequest());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void usernameVacioDevuelve400SinGuardar() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO.replace("\"username\":\"ana\"", "\"username\":\"  \"")))
                .andExpect(status().isBadRequest());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void camposObligatoriosSeValidan() throws Exception {
        for (String json : new String[] {
                JSON_VALIDO.replace("\"nombre\":\"Ana\"", "\"nombre\":\"\""),
                JSON_VALIDO.replace("\"password\":\"ClaveSegura123\"", "\"password\":null"),
                JSON_VALIDO.replace("\"rol\":\"EMPLEADO\"", "\"rol\":null")}) {
            mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void usernameDuplicadoDevuelve409SinGuardar() throws Exception {
        when(usuarios.existsByUsername("ana")).thenReturn(true);
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isConflict());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void duplicadoConcurrenteDeBaseDeDatosTambienDevuelve409() throws Exception {
        when(usuarios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicado", new SQLException("SQL privado", "23000", 1062)));
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isConflict());
    }

    @Test
    void clienteNoPuedeEstablecerIdNiActivo() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO.replace("{", "{\"id\":99,\"activo\":false,")))
                .andExpect(status().isCreated());
        verify(usuarios).saveAndFlush(argThat(usuario ->
                usuario.getId().equals(1L) && Boolean.TRUE.equals(usuario.getActivo())));
    }

    @Test
    void mismaPasswordGeneraHashesDistintosPorLaSal() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isCreated());
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO.replace("\"username\":\"ana\"", "\"username\":\"bea\"")))
                .andExpect(status().isCreated());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios, times(2)).saveAndFlush(captor.capture());
        var usuariosGuardados = captor.getAllValues();
        assertNotEquals(usuariosGuardados.get(0).getPassword(), usuariosGuardados.get(1).getPassword());
        assertTrue(encoder.matches(PASSWORD, usuariosGuardados.get(1).getPassword()));
    }

    @Test
    void configuracionPasswordMantieneBCrypt() {
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
        assertFalse(encoder.matches("incorrecta", encoder.encode(PASSWORD)));
    }
}
