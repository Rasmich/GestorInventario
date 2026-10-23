package com.gestorinventario;

import com.gestorinventario.config.PasswordConfig;
import com.gestorinventario.controller.*;
import com.gestorinventario.error.*;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConflictosDatosTest {
    private static final String PROVEEDOR = "{\"nombre\":\"Proveedor\",\"cif\":\"CIF1\"}";
    private static final String USUARIO = """
            {"nombre":"Usuario","username":"repetido","password":"Password123","rol":"EMPLEADO"}
            """;

    @ParameterizedTest
    @EnumSource(CampoUnico.class)
    void clasificacionComunConservaContextoSinNombreDeIndice(CampoUnico campo) {
        for (SQLException sql : List.of(new SQLException("sin índice", "23000", 1062),
                new SQLException("sin índice", "23505", 0))) {
            var error = new DataIntegrityViolationException("interno", sql);
            var conflicto = assertThrows(ConflictoUnicoException.class,
                    () -> IntegridadDatos.guardar(campo, () -> { throw error; }));
            assertSame(error, conflicto.getCause());
            var respuesta = new GlobalExceptionHandler().manejarNegocio(conflicto);
            assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
            assertEquals(campo.mensaje(), respuesta.getBody().getDetail());
            assertEquals(Map.of(campo.campo(), campo.mensaje()), respuesta.getBody().getProperties().get("erroresCampos"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1451, 1452, 1048, 1364, 1406, 1264, 3819, 0})
    void nuncaConvierteOtrasRestriccionesEnDuplicados(int codigo) {
        var error = new DataIntegrityViolationException("interno", new SQLException("for key 'uk_productos_codigo'", "23000", codigo));
        for (CampoUnico campo : CampoUnico.values())
            assertSame(error, assertThrows(DataIntegrityViolationException.class,
                    () -> IntegridadDatos.guardar(campo, () -> { throw error; })));
    }

    @Test
    void noOcultaErroresInesperadosNiSeBloqueaConCausasCiclicas() {
        var inesperado = new IllegalStateException("inesperado");
        assertSame(inesperado, assertThrows(IllegalStateException.class,
                () -> IntegridadDatos.guardar(CampoUnico.PROVEEDOR, () -> { throw inesperado; })));
        var a = new SQLException("batch");
        var b = new SQLException("duplicado", "23000", 1062);
        a.setNextException(b);
        b.initCause(a);
        assertEquals(2, IntegridadDatos.causas(a).size());
        assertThrows(ConflictoUnicoException.class, () -> IntegridadDatos.guardar(CampoUnico.PROVEEDOR,
                () -> { throw new DataIntegrityViolationException("interno", a); }));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin texto", "Duplicate entry 'CIF1' for key 'proveedores.cif'", "Duplicate entry 'CIF1' for key 'UK_otra'"})
    void proveedoresCreacionYEdicionCapturanSaveYFlush(String texto) throws Exception {
        for (boolean alGuardar : new boolean[]{true, false}) {
            var repositorio = mock(ProveedorRepository.class);
            when(repositorio.findById(2L)).thenReturn(Optional.of(new Proveedor()));
            var error = new DataIntegrityViolationException("interno", new SQLException(texto, "23000", 1062));
            if (alGuardar) when(repositorio.save(any())).thenThrow(error);
            else {
                when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));
                doThrow(error).when(repositorio).flush();
            }
            var mvc = MockMvcBuilders.standaloneSetup(new ProveedorController(
                    new ProveedorService(repositorio, mock(EliminacionService.class))))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            for (var peticion : List.of(post("/api/proveedores"), put("/api/proveedores/2")))
                mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(PROVEEDOR))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.detail").value(CampoUnico.PROVEEDOR.mensaje()))
                        .andExpect(jsonPath("$.erroresCampos.cif").value(CampoUnico.PROVEEDOR.mensaje()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void usuariosPrevalidacionYCarreraAlGuardarEnCreacionYEdicion(boolean prevalidacion) throws Exception {
        var repositorio = mock(UsuarioRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        when(repositorio.existsByUsername("repetido")).thenReturn(prevalidacion);
        when(repositorio.existsByUsernameAndIdNot("repetido", 2L)).thenReturn(prevalidacion);
        when(repositorio.findAdministradoresActivosForUpdate()).thenReturn(List.of(new Usuario("Admin", "admin", "hash", Rol.ADMIN)));
        when(repositorio.findByIdForUpdate(2L)).thenReturn(Optional.of(new Usuario("Empleado", "otro", "hash", Rol.EMPLEADO)));
        when(repositorio.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("interno",
                new SQLException("índice desconocido", "23000", 1062)));
        var mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(new UsuarioService(repositorio, encoder)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (var peticion : List.of(post("/api/usuarios"), put("/api/usuarios/2")))
            mvc.perform(peticion.principal(new UsernamePasswordAuthenticationToken("admin", null))
                            .contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(CampoUnico.USUARIO.mensaje()))
                    .andExpect(jsonPath("$.erroresCampos.username").value(CampoUnico.USUARIO.mensaje()));
        if (prevalidacion) verify(repositorio, never()).saveAndFlush(any());
        else verify(repositorio, times(2)).saveAndFlush(any());
    }

    @Test
    void proveedoresYUsuariosRealesRechazanDuplicadosYConservanDatos() throws Exception {
        try (var contexto = new AnnotationConfigApplicationContext()) {
            contexto.register(CategoriaDuplicadaTest.Config.class, ProveedorService.class, UsuarioService.class, PasswordConfig.class);
            contexto.refresh();
            var proveedores = contexto.getBean(ProveedorRepository.class);
            proveedores.saveAndFlush(new Proveedor("Original", "CIF1", null, null, null));
            var otro = proveedores.saveAndFlush(new Proveedor("Conservar", "CIF2", "123", "a@example.com", "Dirección"));
            var usuarios = contexto.getBean(UsuarioRepository.class);
            usuarios.saveAndFlush(new Usuario("Admin", "admin", "hash-admin", Rol.ADMIN));
            usuarios.saveAndFlush(new Usuario("Original", "repetido", "hash-original", Rol.EMPLEADO));
            var empleado = usuarios.saveAndFlush(new Usuario("Conservar", "otro", "hash-conservar", Rol.EMPLEADO));
            var mvc = MockMvcBuilders.standaloneSetup(new ProveedorController(contexto.getBean(ProveedorService.class)),
                            new UsuarioController(contexto.getBean(UsuarioService.class)))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            for (var peticion : List.of(post("/api/proveedores"), put("/api/proveedores/" + otro.getId())))
                mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(PROVEEDOR))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(CampoUnico.PROVEEDOR.mensaje()));
            for (var peticion : List.of(post("/api/usuarios"), put("/api/usuarios/" + empleado.getId())))
                mvc.perform(peticion.principal(new UsernamePasswordAuthenticationToken("admin", null))
                                .contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(CampoUnico.USUARIO.mensaje()));
            assertEquals(2, proveedores.count());
            var conservado = proveedores.findById(otro.getId()).orElseThrow();
            assertEquals("Conservar", conservado.getNombre());
            assertEquals("CIF2", conservado.getCif());
            assertEquals("123", conservado.getTelefono());
            assertEquals("a@example.com", conservado.getEmail());
            assertEquals(3, usuarios.count());
            var usuario = usuarios.findById(empleado.getId()).orElseThrow();
            assertEquals("Conservar", usuario.getNombre());
            assertEquals("otro", usuario.getUsername());
            assertEquals("hash-conservar", usuario.getPassword());
            assertEquals(Rol.EMPLEADO, usuario.getRol());
            assertTrue(usuario.getActivo());
        }
    }

    @Test
    void validacionLoginDevuelveCamposSinLlamarAutenticacion() throws Exception {
        var auth = mock(AuthService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erroresCampos.username").value("Nombre de usuario: Este campo es obligatorio."))
                .andExpect(jsonPath("$.erroresCampos.password").value("Contraseña: Este campo es obligatorio."));
        verifyNoInteractions(auth);
    }

    @Test
    void validacionesExistentesDeMovimientoIdentificanSoloLosCamposIncorrectos() throws Exception {
        var productos = mock(ProductoRepository.class);
        when(productos.findById(1L)).thenReturn(Optional.of(new Producto()));
        var servicio = new MovimientoService(mock(MovimientoRepository.class), productos, mock(UsuarioRepository.class));
        var mvc = MockMvcBuilders.standaloneSetup(new MovimientoController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/movimientos").principal(new UsernamePasswordAuthenticationToken("admin", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productoId\":1,\"cantidad\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erroresCampos.tipo").value("Selecciona un tipo de movimiento."))
                .andExpect(jsonPath("$.erroresCampos.productoId").doesNotExist());
        mvc.perform(post("/api/movimientos").principal(new UsernamePasswordAuthenticationToken("admin", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productoId\":1,\"tipo\":\"ENTRADA\",\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erroresCampos.cantidad").value("Introduce una cantidad mayor que cero."));
        verify(productos, never()).save(any());
    }

    @Test
    void passwordUtf8RechazadaConservaReglaYSeAsociaAlCampo() throws Exception {
        var repositorio = mock(UsuarioRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(new UsuarioService(repositorio, encoder)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        // 40 caracteres pasan @Size, pero ocupan 80 bytes y el servicio los rechaza.
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(USUARIO.replace("Password123", "á".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erroresCampos.password").value(
                        "La contraseña requiere al menos 8 caracteres y un máximo de 72 bytes UTF-8."));
        verify(repositorio, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @ParameterizedTest
    @ValueSource(ints = {1451, 1452})
    void handlerDistingueClavesForaneasEncadenadas(int codigo) {
        var batch = new SQLException("batch", "HY000", 0);
        batch.setNextException(new SQLException("detalle SQL privado", "23000", codigo));
        var respuesta = new GlobalExceptionHandler().manejarIntegridad(new DataIntegrityViolationException("interno", batch));
        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("La operacion entra en conflicto con relaciones existentes", respuesta.getBody().getDetail());
        assertTrue(respuesta.getBody().getProperties() == null
                || !respuesta.getBody().getProperties().containsKey("erroresCampos"));
    }
}
