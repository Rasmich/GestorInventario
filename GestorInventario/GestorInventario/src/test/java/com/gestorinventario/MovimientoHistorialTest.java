package com.gestorinventario;

import com.gestorinventario.controller.GlobalExceptionHandler;
import com.gestorinventario.controller.MovimientoController;
import com.gestorinventario.model.Movimiento;
import com.gestorinventario.model.Producto;
import com.gestorinventario.model.TipoMovimiento;
import com.gestorinventario.repository.MovimientoRepository;
import com.gestorinventario.repository.ProductoRepository;
import com.gestorinventario.service.MovimientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.data.domain.*;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Controller y Service reales con repositorios mock: sin Spring Boot ni MySQL.
class MovimientoHistorialTest {
    private final MovimientoRepository movimientos = mock(MovimientoRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private MockMvc mvc;
    private Producto producto;
    private Movimiento entrada;
    private Movimiento salida;

    private Page<Movimiento> pagina(List<Movimiento> contenido) {
        return new PageImpl<>(contenido, PageRequest.of(0, 20), contenido.size());
    }

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(
                new MovimientoController(new MovimientoService(movimientos, productos, mock(com.gestorinventario.repository.UsuarioRepository.class))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        producto = new Producto();
        producto.setId(1L);
        producto.setStock(8);
        entrada = new Movimiento(producto, TipoMovimiento.ENTRADA, 3, "Recepcion");
        entrada.setId(10L);
        Producto otroProducto = new Producto();
        otroProducto.setId(2L);
        salida = new Movimiento(otroProducto, TipoMovimiento.SALIDA, 1, "Venta");
        salida.setId(11L);
    }

    @ParameterizedTest
    @EnumSource(TipoMovimiento.class)
    void soloTipoDevuelveMovimientosSinModificarStock(TipoMovimiento tipo) throws Exception {
        Movimiento movimiento = tipo == TipoMovimiento.ENTRADA ? entrada : salida;
        int stockAntes = movimiento.getProducto().getStock();
        when(movimientos.findByTipo(eq(tipo), any(Pageable.class))).thenReturn(pagina(List.of(movimiento)));

        mvc.perform(get("/api/movimientos").param("tipo", tipo.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(movimiento.getId().intValue()))
                .andExpect(jsonPath("$.content[0].tipo").value(tipo.name()));

        assertEquals(stockAntes, movimiento.getProducto().getStock());
        verify(movimientos).findByTipo(eq(tipo), any(Pageable.class));
        verifyNoMoreInteractions(movimientos);
        verifyNoInteractions(productos);
    }

    @ParameterizedTest
    @EnumSource(TipoMovimiento.class)
    void productoYTipoDevuelvenHistorialDeProductoInactivoSinModificarStock(TipoMovimiento tipo)
            throws Exception {
        producto.setActivo(false);
        Movimiento movimiento = new Movimiento(producto, tipo, 2, "Historial");
        when(productos.existsById(1L)).thenReturn(true);
        when(movimientos.findByProductoIdAndTipo(eq(1L), eq(tipo), any(Pageable.class))).thenReturn(pagina(List.of(movimiento)));

        mvc.perform(get("/api/movimientos").param("productoId", "1").param("tipo", tipo.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].producto.id").value(1))
                .andExpect(jsonPath("$.content[0].producto.activo").value(false))
                .andExpect(jsonPath("$.content[0].tipo").value(tipo.name()));

        assertEquals(8, producto.getStock());
        verify(productos).existsById(1L);
        verify(movimientos).findByProductoIdAndTipo(eq(1L), eq(tipo), any(Pageable.class));
        verifyNoMoreInteractions(productos, movimientos);
    }

    @ParameterizedTest
    @EnumSource(TipoMovimiento.class)
    void tipoSinResultadosDevuelveListaVacia(TipoMovimiento tipo) throws Exception {
        when(movimientos.findByTipo(eq(tipo), any(Pageable.class))).thenReturn(pagina(List.of()));
        mvc.perform(get("/api/movimientos").param("tipo", tipo.name()))
                .andExpect(status().isOk()).andExpect(content().json("{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}"));
        verify(movimientos).findByTipo(eq(tipo), any(Pageable.class));
        verifyNoMoreInteractions(movimientos);
        verifyNoInteractions(productos);
    }

    @ParameterizedTest
    @EnumSource(TipoMovimiento.class)
    void productoYTipoSinResultadosDevuelvenListaVacia(TipoMovimiento tipo) throws Exception {
        when(productos.existsById(1L)).thenReturn(true);
        when(movimientos.findByProductoIdAndTipo(eq(1L), eq(tipo), any(Pageable.class))).thenReturn(pagina(List.of()));
        mvc.perform(get("/api/movimientos").param("productoId", "1").param("tipo", tipo.name()))
                .andExpect(status().isOk()).andExpect(content().json("{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}"));
        verify(productos).existsById(1L);
        verify(movimientos).findByProductoIdAndTipo(eq(1L), eq(tipo), any(Pageable.class));
        verifyNoMoreInteractions(productos, movimientos);
        assertEquals(8, producto.getStock());
    }

    @ParameterizedTest
    @EnumSource(TipoMovimiento.class)
    void productoInexistenteConTipoDevuelve404(TipoMovimiento tipo) throws Exception {
        mvc.perform(get("/api/movimientos").param("productoId", "99").param("tipo", tipo.name()))
                .andExpect(status().isNotFound());
        verify(productos).existsById(99L);
        verifyNoMoreInteractions(productos);
        verifyNoInteractions(movimientos);
    }

    @Test
    void tipoInvalidoDevuelve400ConFormatoDeErrorExistente() throws Exception {
        mvc.perform(get("/api/movimientos").param("tipo", "OTRO"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "El cuerpo JSON o los parametros tienen un formato invalido"));
        verifyNoInteractions(productos, movimientos);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 100", "2, 5"})
    void paginacionPersonalizadaYOrdenObligatorio(int page, int size) throws Exception {
        when(movimientos.findAll(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(entrada), PageRequest.of(page, size), 123));
        mvc.perform(get("/api/movimientos").param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)).param("sort", "fecha,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(123))
                .andExpect(jsonPath("$.totalPages").value((123 + size - 1) / size));
        comprobarPageable(page, size);
    }

    private void comprobarPageable(int page, int size) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(movimientos).findAll(captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(page, pageable.getPageNumber());
        assertEquals(size, pageable.getPageSize());
        assertEquals(List.of(Sort.Order.desc("fecha"), Sort.Order.desc("id")),
                pageable.getSort().toList());
        verifyNoMoreInteractions(movimientos);
        verifyNoInteractions(productos);
        assertEquals(8, producto.getStock());
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, -1", "0, 101"})
    void paginacionInvalidaDevuelve400SinConsultar(int page, int size) throws Exception {
        mvc.perform(get("/api/movimientos").param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(productos, movimientos);
    }

    @ParameterizedTest
    @CsvSource({"page, abc", "size, abc"})
    void parametroNumericoInvalidoDevuelve400(String parametro, String valor) throws Exception {
        mvc.perform(get("/api/movimientos").param(parametro, valor))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productos, movimientos);
    }

    @Test
    void paginaFueraDeRangoConservaTotalesYDevuelveContenidoVacio() throws Exception {
        when(movimientos.findAll(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(), PageRequest.of(5, 20), 3));
        mvc.perform(get("/api/movimientos").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
        comprobarPageable(5, 20);
    }

    @Test
    void sinResultadosDevuelvePaginaVacia() throws Exception {
        when(movimientos.findAll(any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(get("/api/movimientos"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}"));
        comprobarPageable(0, 20);
    }

    @Test
    void sinFiltroDevuelveTodosLosMovimientos() throws Exception {
        when(movimientos.findAll(any(Pageable.class))).thenReturn(pagina(List.of(entrada, salida)));

        mvc.perform(get("/api/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[1].id").value(11));

        comprobarPageable(0, 20);
    }

    @Test
    void filtroDevuelveSoloHistorialDelProductoSinModificarStock() throws Exception {
        when(productos.existsById(1L)).thenReturn(true);
        when(movimientos.findByProductoId(eq(1L), any(Pageable.class))).thenReturn(pagina(List.of(entrada)));

        mvc.perform(get("/api/movimientos").param("productoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].producto.id").value(1))
                .andExpect(jsonPath("$.content[0].producto.stock").value(8))
                .andExpect(jsonPath("$.content[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.content[0].cantidad").value(3))
                .andExpect(jsonPath("$.content[0].observaciones").value("Recepcion"));

        assertEquals(8, producto.getStock());
        verify(productos).existsById(1L);
        verify(movimientos).findByProductoId(eq(1L), any(Pageable.class));
        verifyNoMoreInteractions(productos, movimientos);
    }

    @Test
    void productoInexistenteDevuelve404SinConsultarMovimientos() throws Exception {
        mvc.perform(get("/api/movimientos").param("productoId", "99"))
                .andExpect(status().isNotFound());

        verify(productos).existsById(99L);
        verifyNoMoreInteractions(productos);
        verifyNoInteractions(movimientos);
    }

    @Test
    void productoExistenteSinMovimientosDevuelveListaVacia() throws Exception {
        when(productos.existsById(1L)).thenReturn(true);
        when(movimientos.findByProductoId(eq(1L), any(Pageable.class))).thenReturn(pagina(List.of()));

        mvc.perform(get("/api/movimientos").param("productoId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}"));

        verify(productos).existsById(1L);
        verify(movimientos).findByProductoId(eq(1L), any(Pageable.class));
        verifyNoMoreInteractions(productos, movimientos);
    }

    @Test
    void productoInactivoPermiteConsultarHistorialSinModificarStock() throws Exception {
        producto.setActivo(false);
        when(productos.existsById(1L)).thenReturn(true);
        when(movimientos.findByProductoId(eq(1L), any(Pageable.class))).thenReturn(pagina(List.of(entrada)));

        mvc.perform(get("/api/movimientos").param("productoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].producto.activo").value(false));

        assertEquals(8, producto.getStock());
        verify(productos).existsById(1L);
        verify(movimientos).findByProductoId(eq(1L), any(Pageable.class));
        verifyNoMoreInteractions(productos, movimientos);
    }
}
