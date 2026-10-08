package com.gestorinventario;

import com.gestorinventario.controller.*;
import com.gestorinventario.dto.*;
import com.gestorinventario.mapper.ResponseMapper;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class ResponseDtoTest {
    private static final String CATEGORIA_JSON =
            "{\"id\":2,\"nombre\":\"Hardware\",\"descripcion\":\"Componentes\"}";
    private static final String PROVEEDOR_JSON =
            "{\"id\":3,\"nombre\":\"Proveedor A\",\"cif\":\"B12345678\","
                    + "\"telefono\":\"123456789\",\"email\":\"ventas@example.com\",\"direccion\":\"Calle A\"}";
    private static final String PRODUCTO_JSON =
            "{\"id\":1,\"codigo\":\"P1\",\"nombre\":\"Teclado\",\"descripcion\":\"USB\","
                    + "\"precioCompra\":10.00,\"precioVenta\":20.00,\"stock\":2,\"stockMinimo\":2,"
                    + "\"activo\":true,\"version\":7,\"categoria\":" + CATEGORIA_JSON
                    + ",\"proveedor\":" + PROVEEDOR_JSON + ",\"estadoStock\":\"STOCK_BAJO\"}";
    private static final String MOVIMIENTO_JSON =
            "{\"id\":4,\"producto\":" + PRODUCTO_JSON + ",\"tipo\":\"ENTRADA\",\"cantidad\":5,"
                    + "\"fecha\":\"2026-10-01T10:00:00\",\"observaciones\":\"Recepcion\",\"usuario\":null}";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private String token;
    private Producto producto;
    private Categoria categoria;
    private Proveedor proveedor;
    private Movimiento movimiento;

    // Simula propiedades de una entidad/proxy que nunca deben llegar al JSON.
    static class ProductoConCampoInterno extends Producto {
        public String getHibernateLazyInitializer() { return "detalle-interno"; }
    }

    @BeforeEach
    void preparar() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(AutorizacionRolesTest.TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Usuario usuario = new Usuario("Ana", "ana", "hash-interno", Rol.ADMIN);
        usuario.setId(1L);
        when(context.getBean(UsuarioRepository.class).findByUsername("ana")).thenReturn(Optional.of(usuario));
        token = context.getBean(JwtService.class).generarToken(usuario);
        categoria = new Categoria("Hardware", "Componentes");
        categoria.setId(2L);
        proveedor = new Proveedor("Proveedor A", "B12345678", "123456789", "ventas@example.com", "Calle A");
        proveedor.setId(3L);
        producto = spy(new ProductoConCampoInterno());
        producto.setId(1L);
        producto.setCodigo("P1");
        producto.setNombre("Teclado");
        producto.setDescripcion("USB");
        producto.setPrecioCompra(new BigDecimal("10.00"));
        producto.setPrecioVenta(new BigDecimal("20.00"));
        producto.setStock(2);
        producto.setStockMinimo(2);
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
        doReturn(7L).when(producto).getVersion();
        movimiento = new Movimiento(producto, TipoMovimiento.ENTRADA, 5, "Recepcion");
        movimiento.setId(4L);
        movimiento.setFecha(LocalDateTime.of(2026, 10, 1, 10, 0));
        ProductoService productos = context.getBean(ProductoService.class);
        when(productos.obtenerProductos()).thenReturn(List.of(producto));
        when(productos.obtenerProductoPorId(1L)).thenReturn(Optional.of(producto));
        when(productos.obtenerProductosConStockBajo()).thenReturn(List.of(producto));
        when(context.getBean(CategoriaService.class).obtenerCategorias()).thenReturn(List.of(categoria));
        when(context.getBean(CategoriaService.class).obtenerCategoriaPorId(2L)).thenReturn(categoria);
        when(context.getBean(ProveedorService.class).obtenerProveedores()).thenReturn(List.of(proveedor));
        when(context.getBean(ProveedorService.class).obtenerProveedorPorId(3L)).thenReturn(proveedor);
        when(context.getBean(MovimientoService.class).obtenerMovimientos(null, null, 0, 20)).thenReturn(new PageImpl<>(List.of(movimiento), PageRequest.of(0, 20), 1));
    }

    @AfterEach
    void cerrar() {
        context.close();
    }

    @ParameterizedTest
    @CsvSource({
        "/api/productos, PRODUCTO, true",
        "/api/productos/1, PRODUCTO, false",
        "/api/productos/stock-bajo, PRODUCTO, true",
        "/api/categorias, CATEGORIA, true",
        "/api/categorias/2, CATEGORIA, false",
        "/api/proveedores, PROVEEDOR, true",
        "/api/proveedores/3, PROVEEDOR, false",
        "/api/movimientos, MOVIMIENTO, true"
    })
    void jsonConservaTodosLosCamposSinPropiedadesInternas(String ruta, String tipo, boolean lista) throws Exception {
        String esperado = switch (tipo) {
            case "CATEGORIA" -> CATEGORIA_JSON;
            case "PROVEEDOR" -> PROVEEDOR_JSON;
            case "MOVIMIENTO" -> MOVIMIENTO_JSON;
            default -> PRODUCTO_JSON;
        };
        if (lista) {
            esperado = "[" + esperado + "]";
        }
        if (tipo.equals("MOVIMIENTO")) {
            esperado = "{\"content\":" + esperado
                    + ",\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}";
        }
        String respuesta = mvc.perform(get(ruta).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(esperado), mapper.readTree(respuesta));
        assertFalse(respuesta.contains("hibernateLazyInitializer"));
        assertFalse(respuesta.contains("detalle-interno"));
        assertFalse(respuesta.contains("password"));
        assertFalse(respuesta.contains("JWT_SECRET"));
    }

    @ParameterizedTest
    @CsvSource({"1, 2", "2, 2", "0, 0"})
    void stockBajoDevuelve200ListaResponseYUsaConsultaEspecifica(int stock, int stockMinimo)
            throws Exception {
        // El servicio es mock: estos casos validan el contrato HTTP, no el filtrado JPQL.
        producto.setStock(stock);
        producto.setStockMinimo(stockMinimo);
        ProductoService productos = context.getBean(ProductoService.class);
        String esperado = "[" + PRODUCTO_JSON
                .replace("\"stock\":2", "\"stock\":" + stock)
                .replace("\"stockMinimo\":2", "\"stockMinimo\":" + stockMinimo)
                .replace("\"estadoStock\":\"STOCK_BAJO\"", "\"estadoStock\":\""
                        + (stock == 0 ? "SIN_STOCK" : "STOCK_BAJO") + "\"") + "]";

        String respuesta = mvc.perform(get("/api/productos/stock-bajo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(mapper.readTree(esperado), mapper.readTree(respuesta));
        verify(productos).obtenerProductosConStockBajo();
        verifyNoMoreInteractions(productos);

        var dto = context.getBean(ProductoController.class).obtenerProductosConStockBajo().getFirst();
        assertInstanceOf(ProductoResponse.class, dto);
        assertInstanceOf(CategoriaResponse.class, dto.categoria());
        assertInstanceOf(ProveedorResponse.class, dto.proveedor());
        assertEquals(stock, dto.stock());
        assertEquals(stockMinimo, dto.stockMinimo());
    }

    @Test
    void stockBajoSinResultadosDevuelve200YListaVacia() throws Exception {
        ProductoService productos = context.getBean(ProductoService.class);
        when(productos.obtenerProductosConStockBajo()).thenReturn(List.of());

        String respuesta = mvc.perform(get("/api/productos/stock-bajo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(mapper.readTree("[]"), mapper.readTree(respuesta));
        verify(productos).obtenerProductosConStockBajo();
        verifyNoMoreInteractions(productos);
    }

    @Test
    void controllersDevuelvenRecordsDeRespuestaYRelacionesDto() {
        var productos = context.getBean(ProductoController.class);
        ProductoResponse respuesta = productos.obtenerProductos().getFirst();
        assertInstanceOf(ProductoResponse.class, respuesta);
        assertInstanceOf(CategoriaResponse.class, respuesta.categoria());
        assertInstanceOf(ProveedorResponse.class, respuesta.proveedor());
        assertEquals(2, respuesta.stock());
        assertEquals(7L, respuesta.version());
        assertEquals(respuesta, productos.obtenerProductoPorId(1L).getBody());
        assertInstanceOf(CategoriaResponse.class,
                context.getBean(CategoriaController.class).obtenerCategorias().getFirst());
        assertInstanceOf(ProveedorResponse.class,
                context.getBean(ProveedorController.class).obtenerProveedores().getFirst());
        MovimientoResponse respuestaMovimiento =
                context.getBean(MovimientoController.class).obtenerMovimientos(null, null, 0, 20).content().getFirst();
        assertInstanceOf(MovimientoResponse.class, respuestaMovimiento);
        assertEquals(respuesta, respuestaMovimiento.producto());
    }

    @Test
    void postYPutTambienDevuelvenResponses() {
        ProductoService productos = context.getBean(ProductoService.class);
        when(productos.crearProducto(any())).thenReturn(producto);
        when(productos.actualizarProducto(eq(1L), any())).thenReturn(producto);
        ProductoController productoController = context.getBean(ProductoController.class);
        assertInstanceOf(ProductoResponse.class, productoController.crearProducto(new ProductoRequest()).getBody());
        assertInstanceOf(ProductoResponse.class,
                productoController.actualizarProducto(1L, new ProductoRequest()).getBody());
        when(context.getBean(CategoriaService.class).crearCategoria(any())).thenReturn(categoria);
        when(context.getBean(CategoriaService.class).actualizarCategoria(eq(2L), any())).thenReturn(categoria);
        CategoriaController categoriaController = context.getBean(CategoriaController.class);
        assertInstanceOf(CategoriaResponse.class, categoriaController.crearCategoria(new CategoriaRequest()));
        assertInstanceOf(CategoriaResponse.class,
                categoriaController.actualizarCategoria(2L, new CategoriaRequest()).getBody());
        when(context.getBean(ProveedorService.class).crearProveedor(any())).thenReturn(proveedor);
        when(context.getBean(ProveedorService.class).actualizarProveedor(eq(3L), any())).thenReturn(proveedor);
        ProveedorController proveedorController = context.getBean(ProveedorController.class);
        assertInstanceOf(ProveedorResponse.class, proveedorController.crearProveedor(new ProveedorRequest()));
        assertInstanceOf(ProveedorResponse.class,
                proveedorController.actualizarProveedor(3L, new ProveedorRequest()).getBody());
        when(context.getBean(MovimientoService.class).registrarMovimiento(1L, TipoMovimiento.ENTRADA, 5, null, "ana"))
                .thenReturn(movimiento);
        MovimientoRequest request = new MovimientoRequest();
        request.setProductoId(1L);
        request.setTipo(TipoMovimiento.ENTRADA);
        request.setCantidad(5);
        assertInstanceOf(MovimientoResponse.class,
                context.getBean(MovimientoController.class).registrarMovimiento(request, org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("ana", null, List.of())).getBody());
    }

    @Test
    void proveedorOpcionalSeConservaComoNull() {
        producto.setProveedor(null);
        assertNull(ResponseMapper.toProducto(producto).proveedor());
    }

    @Test
    void dtoEsUnaInstantaneaSinReferenciasALaEntidad() {
        ProductoResponse respuesta = ResponseMapper.toProducto(producto);
        categoria.setNombre("Modificada");
        proveedor.setNombre("Modificado");
        producto.setStock(99);
        assertEquals("Hardware", respuesta.categoria().nombre());
        assertEquals("Proveedor A", respuesta.proveedor().nombre());
        assertEquals(2, respuesta.stock());
        assertEquals(7L, respuesta.version());
    }
}
