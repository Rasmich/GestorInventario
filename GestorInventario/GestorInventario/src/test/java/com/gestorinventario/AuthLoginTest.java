package com.gestorinventario;

import com.gestorinventario.config.PasswordConfig;
import com.gestorinventario.controller.AuthController;
import com.gestorinventario.controller.GlobalExceptionHandler;
import com.gestorinventario.model.Rol;
import com.gestorinventario.model.Usuario;
import com.gestorinventario.repository.UsuarioRepository;
import com.gestorinventario.service.AuthService;
import com.gestorinventario.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP simulado, repositorio mock y BCrypt real; no arranca la aplicacion ni conecta a MySQL.
class AuthLoginTest {
    private static final String PASSWORD = "ClaveSegura123";
    private static final String JSON_VALIDO =
            "{\"username\":\"ana\",\"password\":\"ClaveSegura123\"}";
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = spy(new PasswordConfig().passwordEncoder());
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JwtService jwtService = spy(new JwtService(
            java.util.Base64.getEncoder().encodeToString(
                    "clave-ficticia-exclusiva-tests-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            3600000));
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;
    private Usuario usuario;
    private String hash;

    @BeforeEach
    void preparar() {
        hash = encoder.encode(PASSWORD);
        clearInvocations(encoder);
        usuario = new Usuario("Ana", "ana", hash, Rol.EMPLEADO);
        usuario.setId(1L);
        when(usuarios.findByUsername("ana")).thenReturn(Optional.of(usuario));
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(new AuthService(usuarios, encoder, jwtService)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator).build();
    }

    @AfterEach
    void cerrar() {
        validator.close();
    }

    @Test
    void loginCorrectoUsaMatchesContraHashBCryptAlmacenado() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isOk());
        assertNotEquals(PASSWORD, hash);
        assertTrue(hash.startsWith("$2"));
        verify(usuarios).findByUsername("ana");
        verify(encoder).matches(PASSWORD, hash);
        verify(encoder, never()).encode(any());
        verify(usuarios, never()).save(any());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void respuestaContieneSoloCamposSegurosSinPasswordHashNiCookie() throws Exception {
        var respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isOk()).andReturn().getResponse();
        String cuerpo = respuesta.getContentAsString();
        String token = mapper.readTree(cuerpo).get("token").asText();
        assertTrue(jwtService.esValido(token));
        assertEquals("ana", jwtService.extraerUsername(token));
        var camposSinToken = (tools.jackson.databind.node.ObjectNode) mapper.readTree(cuerpo);
        camposSinToken.remove("token");
        assertEquals(mapper.readTree(
                "{\"id\":1,\"nombre\":\"Ana\",\"username\":\"ana\",\"rol\":\"EMPLEADO\"}"),
                camposSinToken);
        assertFalse(cuerpo.contains("password"));
        assertFalse(cuerpo.contains("hash"));
        assertFalse(cuerpo.contains(PASSWORD));
        assertFalse(cuerpo.contains(hash));
        assertNull(respuesta.getHeader("Set-Cookie"));
    }

    @Test
    void passwordIncorrectaDevuelve401() throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO.replace(PASSWORD, "Incorrecta123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals("Credenciales incorrectas", mapper.readTree(cuerpo).get("detail").asText());
        verify(encoder).matches("Incorrecta123", hash);
        verify(encoder, never()).encode(any());
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void usernameInexistenteDevuelveElMismoErrorQuePasswordIncorrecta() throws Exception {
        String inexistente = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO.replace("ana", "inexistente")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String incorrecta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO.replace(PASSWORD, "Incorrecta123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(incorrecta), mapper.readTree(inexistente));
        verify(jwtService, never()).generarToken(any());
        assertEquals("Credenciales incorrectas", mapper.readTree(inexistente).get("detail").asText());
    }

    @Test
    void usuarioInactivoNoPuedeIniciarSesion() throws Exception {
        usuario.setActivo(false);
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertEquals("Credenciales incorrectas", mapper.readTree(cuerpo).get("detail").asText());
        verify(encoder, never()).matches(any(), any());
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void usernameVacioDevuelve400SinConsultarRepositorio() throws Exception {
        for (String json : new String[] {
                JSON_VALIDO.replace("\"ana\"", "\"\""),
                JSON_VALIDO.replace("\"ana\"", "\"   \""),
                "{\"password\":\"ClaveSegura123\"}"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verify(usuarios, never()).findByUsername(any());
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    void passwordVaciaDevuelve400SinConsultarRepositorio() throws Exception {
        for (String json : new String[] {
                JSON_VALIDO.replace(PASSWORD, ""),
                JSON_VALIDO.replace(PASSWORD, "   "),
                "{\"username\":\"ana\"}"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verify(usuarios, never()).findByUsername(any());
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    void errorDeComprobacionBCryptDevuelve401SinExponerDetalles() throws Exception {
        doThrow(new IllegalArgumentException("Detalle interno"))
                .when(encoder).matches(PASSWORD, hash);
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO)).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertEquals("Credenciales incorrectas", mapper.readTree(cuerpo).get("detail").asText());
        assertFalse(cuerpo.contains("Detalle interno"));
    }
}
