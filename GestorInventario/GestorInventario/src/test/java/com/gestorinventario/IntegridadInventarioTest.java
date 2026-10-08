package com.gestorinventario;

import com.gestorinventario.controller.GlobalExceptionHandler;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegridadInventarioTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    @BeforeEach
    void prepararActor() {
        when(usuarios.findByUsername("ana")).thenReturn(Optional.of(new Usuario("Ana", "ana", "hash", Rol.ADMIN)));
    }
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final MovimientoRepository movimientos = mock(MovimientoRepository.class);
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final ProductoService productoService =
            new ProductoService(productos, categorias, proveedores, movimientos);
    private final MovimientoService movimientoService = new MovimientoService(movimientos, productos, usuarios);
    private final EliminacionService eliminacionService =
            new EliminacionService(categorias, proveedores, productos);

    @Test
    void eliminarProductoConHistorialLoDesactivaSinCambiarStock() {
        Producto producto = new Producto();
        producto.setStock(8);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(movimientos.existsByProductoId(1L)).thenReturn(true);
        assertTrue(productoService.eliminarProducto(1L));
        assertFalse(producto.getActivo());
        assertEquals(8, producto.getStock());
        verify(productos).saveAndFlush(producto);
        verify(productos, never()).delete(any(Producto.class));
        verify(movimientos, never()).delete(any(Movimiento.class));
    }

    @Test
    void eliminarProductoSinHistorialLoBorra() {
        Producto producto = new Producto();
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        assertTrue(productoService.eliminarProducto(1L));
        verify(productos).delete(producto);
        verify(productos, never()).saveAndFlush(any());
    }

    @Test
    void productoInexistenteDevuelve404() {
        when(productos.findById(1L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> productoService.eliminarProducto(1L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> movimientoService.registrarMovimiento(1L, TipoMovimiento.ENTRADA, 1, null, "ana"))
                        .getStatusCode());
    }

    @Test
    void productoInactivoRechazaEntradasYSalidasSinGuardar() {
        Producto producto = new Producto();
        producto.setActivo(false);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            assertEquals(HttpStatus.CONFLICT,
                    assertThrows(ResponseStatusException.class,
                            () -> movimientoService.registrarMovimiento(1L, tipo, 1, null, "ana"))
                            .getStatusCode());
        }
        assertEquals(0, producto.getStock());
        verify(productos, never()).save(any());
        verify(movimientos, never()).save(any());
    }

    @Test
    void salidaSinStockYEntradaDesbordadaNoPersisten() {
        Producto producto = new Producto();
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        assertThrows(IllegalArgumentException.class,
                () -> movimientoService.registrarMovimiento(1L, TipoMovimiento.SALIDA, 1, null, "ana"));
        producto.setStock(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> movimientoService.registrarMovimiento(1L, TipoMovimiento.ENTRADA, 1, null, "ana"));
        assertEquals(Integer.MAX_VALUE, producto.getStock());
        verify(movimientos, never()).save(any());
    }

    @Test
    void categoriaUtilizadaNoSeBorra() {
        when(categorias.findById(1L)).thenReturn(Optional.of(new Categoria()));
        when(productos.existsByCategoriaId(1L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> eliminacionService.eliminarCategoria(1L)).getStatusCode());
        verify(categorias, never()).delete(any(Categoria.class));
    }

    @Test
    void proveedorUtilizadoNoSeBorra() {
        when(proveedores.findById(1L)).thenReturn(Optional.of(new Proveedor()));
        when(productos.existsByProveedorId(1L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> eliminacionService.eliminarProveedor(1L)).getStatusCode());
        verify(proveedores, never()).delete(any(Proveedor.class));
    }

    @Test
    void categoriaYProveedorSinReferenciasSeBorran() {
        Categoria categoria = new Categoria();
        Proveedor proveedor = new Proveedor();
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria));
        when(proveedores.findById(1L)).thenReturn(Optional.of(proveedor));
        eliminacionService.eliminarCategoria(1L);
        eliminacionService.eliminarProveedor(1L);
        verify(categorias).delete(categoria);
        verify(proveedores).delete(proveedor);
    }

    @Test
    void erroresDeIntegridadYConcurrenciaUsanEstadoAdecuadoSinExponerSQL() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        for (int codigo : new int[] {1062, 1451, 1452}) {
            var respuesta = handler.manejarIntegridad(new DataIntegrityViolationException(
                    "SQL privado", new SQLException("SQL privado", "23000", codigo)));
            assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
            assertFalse(respuesta.getBody().getDetail().contains("SQL privado"));
        }
        var invalido = handler.manejarIntegridad(new DataIntegrityViolationException(
                "SQL privado", new SQLException("SQL privado", "23000", 1048)));
        assertEquals(HttpStatus.BAD_REQUEST, invalido.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler.manejarConcurrencia(
                new OptimisticLockingFailureException("conflicto")).getStatusCode());
    }
}
