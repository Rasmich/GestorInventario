package com.gestorinventario;

import com.gestorinventario.config.*;
import com.gestorinventario.controller.*;
import com.gestorinventario.dto.UsuarioActualizacionRequest;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** HTTP, JWT, BCrypt, servicios y JPA reales; exclusivamente H2 en memoria. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsuarioGestionTest {
    @Configuration
    @EnableWebMvc
    @Import({StockAlertasTest.Config.class, SecurityConfig.class, PasswordConfig.class,
            UsuarioService.class, AuthService.class, UsuarioController.class, AuthController.class,
            ProductoController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean JwtService jwtService() {
            return new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 3600000);
        }
    }

    private static final String PASSWORD = "EntradaPrueba123";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private UsuarioRepository usuarios;
    private UsuarioService service;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private Usuario admin;
    private Usuario otroAdmin;
    private Usuario empleado;
    private String tokenAdmin;
    private String tokenEmpleado;
    private String hashInicial;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeAll void iniciar() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuarios = context.getBean(UsuarioRepository.class);
        service = context.getBean(UsuarioService.class);
        encoder = context.getBean(PasswordEncoder.class);
        jwt = context.getBean(JwtService.class);
        hashInicial = encoder.encode(PASSWORD);
    }

    @BeforeEach void preparar() {
        context.getBean(MovimientoRepository.class).deleteAll();
        context.getBean(ProductoRepository.class).deleteAll();
        context.getBean(CategoriaRepository.class).deleteAll();
        context.getBean(ProveedorRepository.class).deleteAll();
        usuarios.deleteAll();
        admin = usuarios.saveAndFlush(new Usuario("Administrador", "admin", hashInicial, Rol.ADMIN));
        otroAdmin = usuarios.saveAndFlush(new Usuario("Otro administrador", "admin2", hashInicial, Rol.ADMIN));
        empleado = usuarios.saveAndFlush(new Usuario("Empleado", "empleado", hashInicial, Rol.EMPLEADO));
        tokenAdmin = jwt.generarToken(admin);
        tokenEmpleado = jwt.generarToken(empleado);
    }

    @AfterAll void cerrar() { if (context != null) context.close(); }

    private String datos(String username, Rol rol) {
        return "{\"nombre\":\"Nombre modificado\",\"username\":\"" + username + "\",\"rol\":\"" + rol + "\"}";
    }

    private JsonNode json(String respuesta) { return mapper.readTree(respuesta); }

    private void respuestaSegura(JsonNode usuario) {
        assertEquals(5, usuario.size());
        for (String campo : List.of("id", "nombre", "username", "rol", "activo")) assertTrue(usuario.has(campo));
        assertFalse(usuario.has("password"));
        assertFalse(usuario.has("hash"));
    }

    @Test void consultaListaYDetalleSinCredenciales() throws Exception {
        String lista = mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(3, json(lista).size());
        json(lista).forEach(this::respuestaSegura);
        assertFalse(lista.contains(hashInicial));
        assertFalse(lista.contains(PASSWORD));
        String detalle = mvc.perform(get("/api/usuarios/" + empleado.getId()).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        respuestaSegura(json(detalle));
        assertEquals(empleado.getId().longValue(), json(detalle).get("id").asLong());
    }

    @ParameterizedTest
    @CsvSource({"LISTA", "DETALLE", "CREAR", "MODIFICAR", "ESTADO", "PASSWORD"})
    void todosLosEndpointsExigenAdmin(String operacion) throws Exception {
        for (String acceso : List.of("ADMIN", "EMPLEADO", "SIN_TOKEN", "INVALIDO", "INACTIVO")) {
            // Restablecer la fila del actor para cada variante de este endpoint.
            admin.setActivo(!acceso.equals("INACTIVO"));
            usuarios.saveAndFlush(admin);
            var request = switch (operacion) {
                case "LISTA" -> get("/api/usuarios");
                case "DETALLE" -> get("/api/usuarios/" + empleado.getId());
                case "CREAR" -> post("/api/usuarios").content("{\"nombre\":\"Nuevo\",\"username\":\"nuevo\",\"rol\":\"EMPLEADO\",\"password\":\"" + PASSWORD + "\"}");
                case "MODIFICAR" -> put("/api/usuarios/" + empleado.getId()).content(datos("empleado", Rol.EMPLEADO));
                case "ESTADO" -> patch("/api/usuarios/" + empleado.getId() + "/estado").content("{\"activo\":true}");
                default -> patch("/api/usuarios/" + empleado.getId() + "/password").content("{\"password\":\"" + PASSWORD + "\"}");
            };
            request.contentType(MediaType.APPLICATION_JSON);
            if (!acceso.equals("SIN_TOKEN")) {
                request.header("Authorization", "Bearer " + (acceso.equals("EMPLEADO") ? tokenEmpleado
                        : acceso.equals("INVALIDO") ? "invalido" : tokenAdmin));
            }
            int esperado = acceso.equals("ADMIN") ? (operacion.equals("CREAR") ? 201 : 200)
                    : acceso.equals("EMPLEADO") ? 403 : 401;
            long antes = usuarios.count();
            String respuesta = mvc.perform(request).andExpect(status().is(esperado))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(respuesta.contains(hashInicial));
            assertFalse(respuesta.contains(PASSWORD));
            if (esperado == 401 || esperado == 403) assertEquals(antes, usuarios.count());
            else if (!operacion.equals("LISTA")) respuestaSegura(json(respuesta));
        }
    }

    @ParameterizedTest
    @CsvSource({"GET", "PUT", "ESTADO", "PASSWORD"})
    void usuarioInexistenteDevuelve404(String operacion) throws Exception {
        var request = switch (operacion) {
            case "GET" -> get("/api/usuarios/999999");
            case "PUT" -> put("/api/usuarios/999999").content(datos("otro", Rol.EMPLEADO));
            case "ESTADO" -> patch("/api/usuarios/999999/estado").content("{\"activo\":false}");
            default -> patch("/api/usuarios/999999/password").content("{\"password\":\"" + PASSWORD + "\"}");
        };
        mvc.perform(request.header("Authorization", "Bearer " + tokenAdmin).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        assertEquals(3, usuarios.count());
    }

    @Test void modificacionConservaIdPasswordEstadoYMovimientos() throws Exception {
        Movimiento movimiento = crearMovimiento();
        mvc.perform(put("/api/usuarios/" + empleado.getId()).header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(datos("empleado-renombrado", Rol.ADMIN)
                                .replace("}", ",\"id\":999,\"activo\":false,\"password\":\"ignorada\"}")))
                .andExpect(status().isOk());
        Usuario actualizado = usuarios.findById(empleado.getId()).orElseThrow();
        assertEquals("Nombre modificado", actualizado.getNombre());
        assertEquals("empleado-renombrado", actualizado.getUsername());
        assertEquals(Rol.ADMIN, actualizado.getRol());
        assertTrue(actualizado.getActivo());
        assertEquals(hashInicial, actualizado.getPassword());
        Movimiento conservado = context.getBean(MovimientoRepository.class).findById(movimiento.getId()).orElseThrow();
        assertEquals(empleado.getId(), conservado.getUsuario().getId());
        assertEquals(movimiento.getCantidad(), conservado.getCantidad());
        assertEquals(1, context.getBean(MovimientoRepository.class).count());
        assertEquals(3, usuarios.count());
    }

    @Test void duplicadosEnCreacionYModificacionDevuelven409() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Otro\",\"username\":\"empleado\",\"rol\":\"EMPLEADO\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/usuarios/" + empleado.getId()).header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(datos("admin", Rol.ADMIN)))
                .andExpect(status().isConflict());
        assertEquals("empleado", usuarios.findById(empleado.getId()).orElseThrow().getUsername());
        assertEquals(3, usuarios.count());
    }

    @ParameterizedTest
    @CsvSource({"PUT,NOMBRE_VACIO", "PUT,USERNAME_VACIO", "PUT,USERNAME_ESPACIOS", "PUT,NOMBRE_LARGO",
            "PUT,ROL_NULO", "PUT,ROL_INVALIDO", "ESTADO,AUSENTE", "ESTADO,NULO",
            "PASSWORD,AUSENTE", "PASSWORD,CORTA", "PASSWORD,LARGA", "PASSWORD,UTF8_LARGA"})
    void datosIncorrectosDevuelven400SinModificar(String operacion, String error) throws Exception {
        String cuerpo = switch (error) {
            case "NOMBRE_VACIO" -> datos("empleado", Rol.EMPLEADO).replace("Nombre modificado", " ");
            case "USERNAME_VACIO" -> datos("", Rol.EMPLEADO);
            case "USERNAME_ESPACIOS" -> datos("dos palabras", Rol.EMPLEADO);
            case "NOMBRE_LARGO" -> datos("empleado", Rol.EMPLEADO).replace("Nombre modificado", "a".repeat(256));
            case "ROL_NULO" -> datos("empleado", Rol.EMPLEADO).replace("\"EMPLEADO\"", "null");
            case "ROL_INVALIDO" -> datos("empleado", Rol.EMPLEADO).replace("EMPLEADO", "OTRO");
            case "NULO" -> "{\"activo\":null}";
            case "CORTA" -> "{\"password\":\"corta\"}";
            case "LARGA" -> "{\"password\":\"" + "a".repeat(73) + "\"}";
            case "UTF8_LARGA" -> "{\"password\":\"" + "\u00e9".repeat(37) + "\"}";
            default -> "{}";
        };
        var request = operacion.equals("PUT") ? put("/api/usuarios/" + empleado.getId())
                : patch("/api/usuarios/" + empleado.getId() + (operacion.equals("ESTADO") ? "/estado" : "/password"));
        mvc.perform(request.header("Authorization", "Bearer " + tokenAdmin).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
        Usuario conservado = usuarios.findById(empleado.getId()).orElseThrow();
        assertEquals("Empleado", conservado.getNombre());
        assertEquals("empleado", conservado.getUsername());
        assertEquals(Rol.EMPLEADO, conservado.getRol());
        assertTrue(conservado.getActivo());
        assertEquals(hashInicial, conservado.getPassword());
    }

    @Test void desactivacionBloqueaLoginYJwtPrevioYActivacionRestauraAcceso() throws Exception {
        Movimiento movimiento = crearMovimiento();
        mvc.perform(patch("/api/usuarios/" + empleado.getId() + "/estado").header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        login("empleado", PASSWORD, 401);
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + tokenEmpleado)).andExpect(status().isUnauthorized());
        assertEquals(empleado.getId(), context.getBean(MovimientoRepository.class).findById(movimiento.getId()).orElseThrow().getUsuario().getId());
        mvc.perform(patch("/api/usuarios/" + empleado.getId() + "/estado").header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isOk());
        login("empleado", PASSWORD, 200);
    }

    @Test void administradorNoPuedeDesactivarseAunqueExistaOtro() throws Exception {
        mvc.perform(patch("/api/usuarios/" + admin.getId() + "/estado").header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isConflict());
        assertTrue(usuarios.findById(admin.getId()).orElseThrow().getActivo());
    }

    @Test void ultimoAdministradorActivoNoPuedePerderSuRolNiDesactivarse() throws Exception {
        otroAdmin.setActivo(false);
        usuarios.saveAndFlush(otroAdmin);
        mvc.perform(put("/api/usuarios/" + admin.getId()).header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(datos("admin", Rol.EMPLEADO)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/usuarios/" + admin.getId() + "/estado").header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isConflict());
        assertEquals(Rol.ADMIN, usuarios.findById(admin.getId()).orElseThrow().getRol());
        assertTrue(usuarios.findById(admin.getId()).orElseThrow().getActivo());
    }

    @Test void puedeDesactivarOtroAdministradorSiQuedaUnoActivo() throws Exception {
        mvc.perform(patch("/api/usuarios/" + otroAdmin.getId() + "/estado").header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        assertFalse(usuarios.findById(otroAdmin.getId()).orElseThrow().getActivo());
        assertTrue(usuarios.findById(admin.getId()).orElseThrow().getActivo());
    }

    @Test void cambioDePasswordUsaBCryptConservaDatosYPermiteNuevoLogin() throws Exception {
        Movimiento movimiento = crearMovimiento();
        String nueva = "NuevaEntradaPrueba456";
        String respuesta = mvc.perform(patch("/api/usuarios/" + empleado.getId() + "/password")
                .header("Authorization", "Bearer " + tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + nueva + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        respuestaSegura(json(respuesta));
        Usuario actualizado = usuarios.findById(empleado.getId()).orElseThrow();
        assertTrue(encoder.matches(nueva, actualizado.getPassword()));
        assertFalse(encoder.matches(PASSWORD, actualizado.getPassword()));
        assertFalse(respuesta.contains(nueva));
        assertFalse(respuesta.contains(actualizado.getPassword()));
        assertEquals(empleado.getId(), actualizado.getId());
        assertEquals("Empleado", actualizado.getNombre());
        assertEquals("empleado", actualizado.getUsername());
        assertEquals(Rol.EMPLEADO, actualizado.getRol());
        assertTrue(actualizado.getActivo());
        assertEquals(empleado.getId(), context.getBean(MovimientoRepository.class).findById(movimiento.getId()).orElseThrow().getUsuario().getId());
        login("empleado", PASSWORD, 401);
        login("empleado", nueva, 200);
    }

    @Test void jwtAnteriorUsaRolActualTrasPromocionYRevocacion() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tokenEmpleado)).andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/" + empleado.getId()).header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(datos("empleado", Rol.ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tokenEmpleado)).andExpect(status().isOk());
        mvc.perform(put("/api/usuarios/" + empleado.getId()).header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(datos("empleado", Rol.EMPLEADO))).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tokenEmpleado)).andExpect(status().isForbidden());
    }

    @Test void renombrarYReutilizarUsernameNoTransfiereUnJwtAnterior() throws Exception {
        mvc.perform(put("/api/usuarios/" + empleado.getId()).header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(datos("renombrado", Rol.EMPLEADO))).andExpect(status().isOk());
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + tokenEmpleado)).andExpect(status().isUnauthorized());
        Usuario nuevaCuenta = usuarios.saveAndFlush(new Usuario("Otra persona", "empleado", hashInicial, Rol.ADMIN));
        assertNotEquals(empleado.getId(), nuevaCuenta.getId());
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tokenEmpleado)).andExpect(status().isUnauthorized());
        login("renombrado", PASSWORD, 200);
    }

    @Test void revalidaActorSiDejaDeSerAdminAntesDeLaTransaccion() {
        admin.setRol(Rol.EMPLEADO);
        usuarios.saveAndFlush(admin);
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.cambiarEstado(empleado.getId(), false, "admin"));
        assertEquals(403, exception.getStatusCode().value());
        assertTrue(usuarios.findById(empleado.getId()).orElseThrow().getActivo());
    }

    @Test void dosDemocionesConcurrentesConservanUnAdministradorActivo() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(() -> demover(inicio, admin));
            var segunda = executor.submit(() -> demover(inicio, otroAdmin));
            inicio.countDown();
            var resultados = List.of(primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));
            assertEquals(1, resultados.stream().filter(codigo -> codigo == 200).count());
            assertEquals(1, resultados.stream().filter(codigo -> codigo == 409).count());
            assertEquals(1, usuarios.findAll().stream().filter(u -> u.getRol() == Rol.ADMIN && u.getActivo()).count());
        }
    }

    private int demover(CountDownLatch inicio, Usuario actor) throws Exception {
        inicio.await();
        try {
            service.actualizarUsuario(actor.getId(), new UsuarioActualizacionRequest(actor.getNombre(), actor.getUsername(), Rol.EMPLEADO), actor.getUsername());
            return 200;
        } catch (ResponseStatusException exception) { return exception.getStatusCode().value(); }
    }

    private void login(String username, String password, int esperado) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        assertFalse(respuesta.contains(password));
        assertFalse(respuesta.contains(hashInicial));
        if (esperado == 200) assertTrue(jwt.esValido(json(respuesta).get("token").asText()));
    }

    private Movimiento crearMovimiento() {
        Categoria categoria = context.getBean(CategoriaRepository.class).save(new Categoria("Categoria", null));
        Proveedor proveedor = context.getBean(ProveedorRepository.class).save(new Proveedor("Proveedor", "CIF-TEST", null, null, null));
        Producto producto = context.getBean(ProductoRepository.class).save(new Producto("P1", "Producto", null,
                BigDecimal.ONE, BigDecimal.TEN, 0, true, categoria, proveedor));
        Movimiento movimiento = new Movimiento(producto, TipoMovimiento.ENTRADA, 3, "Historial conservado");
        movimiento.setUsuario(empleado);
        return context.getBean(MovimientoRepository.class).saveAndFlush(movimiento);
    }
}
