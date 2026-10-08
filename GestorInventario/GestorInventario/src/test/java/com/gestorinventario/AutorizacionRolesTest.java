package com.gestorinventario;

import com.gestorinventario.controller.*;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

// Cadena de seguridad y controladores reales; servicios de inventario mock y sin MySQL.
class AutorizacionRolesTest {
    @Configuration
    @Import({JwtSecurityTest.TestConfig.class, CategoriaController.class,
            ProveedorController.class, MovimientoController.class})
    static class TestConfig {
        @Bean CategoriaService categoriaService() { return mock(CategoriaService.class); }
        @Bean ProveedorService proveedorService() { return mock(ProveedorService.class); }
        @Bean MovimientoService movimientoService() { return mock(MovimientoService.class); }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private Usuario usuario;
    private String token;
    private ProductoService productos;
    private CategoriaService categorias;
    private ProveedorService proveedores;
    private MovimientoService movimientos;
    private UsuarioRepository usuarios;

    @BeforeEach
    void preparar() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuarios = context.getBean(UsuarioRepository.class);
        usuario = new Usuario("Ana", "ana", "hash-no-utilizado-en-estas-pruebas", Rol.EMPLEADO);
        usuario.setId(1L);
        when(usuarios.findByUsername("ana")).thenReturn(Optional.of(usuario));
        token = context.getBean(JwtService.class).generarToken(usuario);
        productos = context.getBean(ProductoService.class);
        categorias = context.getBean(CategoriaService.class);
        proveedores = context.getBean(ProveedorService.class);
        movimientos = context.getBean(MovimientoService.class);
        Producto producto = new Producto();
        producto.setId(1L);
        when(productos.obtenerProductos()).thenReturn(List.of());
        when(productos.obtenerProductoPorId(1L)).thenReturn(Optional.of(producto));
        when(productos.obtenerProductosConStockBajo()).thenReturn(List.of());
        when(productos.crearProducto(any())).thenReturn(producto);
        when(productos.actualizarProducto(eq(1L), any())).thenReturn(producto);
        when(productos.eliminarProducto(1L)).thenReturn(true);
        when(categorias.obtenerCategorias()).thenReturn(List.of());
        when(categorias.obtenerCategoriaPorId(1L)).thenReturn(new Categoria());
        when(categorias.crearCategoria(any())).thenReturn(new Categoria());
        when(categorias.actualizarCategoria(eq(1L), any())).thenReturn(new Categoria());
        when(proveedores.obtenerProveedores()).thenReturn(List.of());
        when(proveedores.obtenerProveedorPorId(1L)).thenReturn(new Proveedor());
        when(proveedores.crearProveedor(any())).thenReturn(new Proveedor());
        when(proveedores.actualizarProveedor(eq(1L), any())).thenReturn(new Proveedor());
        when(movimientos.obtenerMovimientos(null, null, 0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));
        when(movimientos.obtenerMovimientos(1L, null, 0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));
        when(movimientos.obtenerMovimientos(null, TipoMovimiento.ENTRADA, 0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));
        when(movimientos.obtenerMovimientos(1L, TipoMovimiento.SALIDA, 0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));
        when(movimientos.registrarMovimiento(eq(1L), any(), eq(1), any(), eq("ana")))
                .thenReturn(new Movimiento(producto, TipoMovimiento.ENTRADA, 1, null));
        when(usuarios.saveAndFlush(any())).thenAnswer(invocation -> {
            Usuario nuevo = invocation.getArgument(0);
            nuevo.setId(2L);
            return nuevo;
        });
    }

    @AfterEach
    void cerrar() {
        context.close();
    }

    @ParameterizedTest
    @CsvSource({
        "SIN_TOKEN, GET, /api/productos, 401",
        "SIN_TOKEN, GET, /api/movimientos?productoId=1, 401",
        "SIN_TOKEN, GET, /api/movimientos?tipo=ENTRADA, 401",
        "ADMIN, GET, /api/movimientos?tipo=ENTRADA, 200",
        "EMPLEADO, GET, /api/movimientos?tipo=ENTRADA, 200",
        "SIN_TOKEN, GET, /api/movimientos?productoId=1&tipo=SALIDA, 401",
        "ADMIN, GET, /api/movimientos?productoId=1&tipo=SALIDA, 200",
        "EMPLEADO, GET, /api/movimientos?productoId=1&tipo=SALIDA, 200",
        "ADMIN, GET, /api/movimientos?productoId=1, 200",
        "EMPLEADO, GET, /api/movimientos?productoId=1, 200",
        "SIN_TOKEN, GET, /api/productos/stock-bajo, 401",
        "SIN_TOKEN, POST, /api/productos, 401",
        "SIN_TOKEN, POST, /api/usuarios, 401",
        "ADMIN, GET, /api/productos, 200",
        "ADMIN, GET, /api/productos/1, 200",
        "ADMIN, GET, /api/productos/stock-bajo, 200",
        "ADMIN, POST, /api/productos, 200",
        "ADMIN, PUT, /api/productos/1, 200",
        "ADMIN, DELETE, /api/productos/1, 204",
        "ADMIN, POST, /api/usuarios, 201",
        "ADMIN, POST, /api/movimientos, 200",
        "ADMIN, POST, /api/categorias, 200",
        "ADMIN, PUT, /api/categorias/1, 200",
        "ADMIN, DELETE, /api/categorias/1, 204",
        "ADMIN, POST, /api/proveedores, 200",
        "ADMIN, PUT, /api/proveedores/1, 200",
        "ADMIN, DELETE, /api/proveedores/1, 204",
        "EMPLEADO, GET, /api/productos, 200",
        "EMPLEADO, GET, /api/productos/1, 200",
        "EMPLEADO, GET, /api/productos/stock-bajo, 200",
        "EMPLEADO, GET, /api/categorias, 200",
        "EMPLEADO, GET, /api/categorias/1, 200",
        "EMPLEADO, GET, /api/proveedores, 200",
        "EMPLEADO, GET, /api/proveedores/1, 200",
        "EMPLEADO, GET, /api/movimientos, 200",
        "EMPLEADO, POST, /api/movimientos, 200",
        "EMPLEADO, POST, /api/productos, 403",
        "EMPLEADO, PUT, /api/productos/1, 403",
        "EMPLEADO, DELETE, /api/productos/1, 403",
        "EMPLEADO, POST, /api/categorias, 403",
        "EMPLEADO, PUT, /api/categorias/1, 403",
        "EMPLEADO, DELETE, /api/categorias/1, 403",
        "EMPLEADO, POST, /api/proveedores, 403",
        "EMPLEADO, PUT, /api/proveedores/1, 403",
        "EMPLEADO, DELETE, /api/proveedores/1, 403",
        "EMPLEADO, POST, /api/usuarios, 403"
    })
    void verificaPoliticaDeRoles(String rol, String metodo, String ruta, int esperado) throws Exception {
        var request = request(HttpMethod.valueOf(metodo), ruta);
        if (!rol.equals("SIN_TOKEN")) {
            // El JWT original tiene EMPLEADO; la autoridad se toma del usuario actual.
            usuario.setRol(Rol.valueOf(rol));
            request.header("Authorization", "Bearer " + token);
        }
        if (metodo.equals("POST") || metodo.equals("PUT")) {
            request.contentType(MediaType.APPLICATION_JSON).content(cuerpoValido(ruta));
        }
        var resultado = mvc.perform(request).andExpect(status().is(esperado)).andReturn();
        if (ruta.equals("/api/movimientos?productoId=1") && esperado == 200) {
            verify(movimientos).obtenerMovimientos(1L, null, 0, 20);
            verifyNoMoreInteractions(movimientos);
        }
        if (ruta.equals("/api/movimientos?tipo=ENTRADA") && esperado == 200) {
            verify(movimientos).obtenerMovimientos(null, TipoMovimiento.ENTRADA, 0, 20);
            verifyNoMoreInteractions(movimientos);
        }
        if (ruta.equals("/api/movimientos?productoId=1&tipo=SALIDA") && esperado == 200) {
            verify(movimientos).obtenerMovimientos(1L, TipoMovimiento.SALIDA, 0, 20);
            verifyNoMoreInteractions(movimientos);
        }
        if (ruta.equals("/api/productos/stock-bajo") && esperado == 200) {
            verify(productos).obtenerProductosConStockBajo();
            verifyNoMoreInteractions(productos);
        }
        if (esperado == 401 || esperado == 403) {
            assertTrue(MediaType.valueOf("application/problem+json").isCompatibleWith(
                    MediaType.parseMediaType(resultado.getResponse().getContentType())));
            var json = JsonMapper.builder().build().readTree(resultado.getResponse().getContentAsString());
            assertEquals(esperado, json.get("status").asInt());
            assertNull(resultado.getResponse().getHeader("Location"));
            if (esperado == 403) {
                assertNull(resultado.getResponse().getHeader("WWW-Authenticate"));
            }
            verifyNoInteractions(productos, categorias, proveedores, movimientos);
            verify(usuarios, never()).saveAndFlush(any());
        }
    }

    @ParameterizedTest
    @CsvSource({"ADMIN, 200", "EMPLEADO, 200", "SIN_TOKEN, 401"})
    void historialPaginadoRespetaSeguridad(String rol, int esperado) throws Exception {
        when(movimientos.obtenerMovimientos(1L, TipoMovimiento.SALIDA, 2, 5))
                .thenReturn(Page.empty(PageRequest.of(2, 5)));
        var request = get("/api/movimientos").param("productoId", "1").param("tipo", "SALIDA")
                .param("page", "2").param("size", "5");
        if (!rol.equals("SIN_TOKEN")) {
            usuario.setRol(Rol.valueOf(rol));
            request.header("Authorization", "Bearer " + token);
        }
        mvc.perform(request).andExpect(status().is(esperado));
        if (esperado == 200) {
            verify(movimientos).obtenerMovimientos(1L, TipoMovimiento.SALIDA, 2, 5);
            verifyNoMoreInteractions(movimientos);
        } else {
            verifyNoInteractions(movimientos);
        }
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,200", "EMPLEADO,200", "SIN_TOKEN,401", "TOKEN_INVALIDO,401", "INACTIVO,401"})
    void alertasConContenidoRespetanJwtYPermisos(String acceso, int esperado) throws Exception {
        Producto producto = new Producto();
        producto.setId(10L);
        producto.setNombre("Producto de prueba");
        producto.setStock(0);
        producto.setStockMinimo(5);
        when(productos.obtenerProductosConStockBajo()).thenReturn(List.of(producto));
        var request = get("/api/productos/stock-bajo");
        switch (acceso) {
            case "ADMIN", "EMPLEADO" -> {
                usuario.setRol(Rol.valueOf(acceso));
                request.header("Authorization", "Bearer " + token);
            }
            case "TOKEN_INVALIDO" -> request.header("Authorization", "Bearer invalido");
            case "INACTIVO" -> {
                usuario.setActivo(false);
                request.header("Authorization", "Bearer " + token);
            }
        }
        String respuesta = mvc.perform(request).andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        if (esperado == 200) {
            var json = JsonMapper.builder().build().readTree(respuesta);
            assertEquals(1, json.size());
            assertEquals(10L, json.get(0).get("id").asLong());
            assertEquals(0, json.get(0).get("stock").asInt());
            assertEquals(5, json.get(0).get("stockMinimo").asInt());
            assertEquals("SIN_STOCK", json.get(0).get("estadoStock").asText());
            verify(productos).obtenerProductosConStockBajo();
            verifyNoMoreInteractions(productos);
        } else {
            verifyNoInteractions(productos);
        }
    }

    @Test
    void cambioDeRolODesactivacionTieneEfectoConTokenAnterior() throws Exception {
        usuario.setRol(Rol.ADMIN);
        String tokenAdmin = context.getBean(JwtService.class).generarToken(usuario);
        mvc.perform(delete("/api/productos/1").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());
        usuario.setRol(Rol.EMPLEADO);
        mvc.perform(delete("/api/productos/1").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
        usuario.setActivo(false);
        mvc.perform(delete("/api/productos/1").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isUnauthorized());
        verify(productos, times(1)).eliminarProducto(1L);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN, ENTRADA", "EMPLEADO, ENTRADA", "ADMIN, SALIDA", "EMPLEADO, SALIDA"})
    void registroTransmiteIdentidadJwtParaAmbosRoles(Rol rol, TipoMovimiento tipo) throws Exception {
        usuario.setRol(rol);
        mvc.perform(post("/api/movimientos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"tipo\":\"" + tipo.name()
                                + "\",\"cantidad\":1,\"usuarioId\":99,\"username\":\"otro\"}"))
                .andExpect(status().isOk());
        verify(movimientos).registrarMovimiento(1L, tipo, 1, null, "ana");
        verifyNoMoreInteractions(movimientos);
    }

    @Test
    void empleadoPuedeRegistrarSalida() throws Exception {
        mvc.perform(post("/api/movimientos").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productoId\":1,\"tipo\":\"SALIDA\",\"cantidad\":1}"))
                .andExpect(status().isOk());
        verify(movimientos).registrarMovimiento(1L, TipoMovimiento.SALIDA, 1, null, "ana");
    }

    private String cuerpoValido(String ruta) {
        if (ruta.startsWith("/api/productos")) {
            return "{\"codigo\":\"P1\",\"nombre\":\"Producto\",\"precioCompra\":1,"
                    + "\"precioVenta\":2,\"stockMinimo\":0,\"categoriaId\":1,\"proveedorId\":1}";
        }
        if (ruta.startsWith("/api/categorias")) {
            return "{\"nombre\":\"Categoria\"}";
        }
        if (ruta.startsWith("/api/proveedores")) {
            return "{\"nombre\":\"Proveedor\",\"cif\":\"B12345678\"}";
        }
        if (ruta.equals("/api/movimientos")) {
            return "{\"productoId\":1,\"tipo\":\"ENTRADA\",\"cantidad\":1}";
        }
        return "{\"nombre\":\"Bea\",\"username\":\"bea\",\"password\":\"ClaveSegura123\",\"rol\":\"EMPLEADO\"}";
    }
}
