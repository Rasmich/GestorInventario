package com.gestorinventario;

import com.gestorinventario.dto.ProductoRequest;
import com.gestorinventario.model.Categoria;
import com.gestorinventario.model.Producto;
import com.gestorinventario.model.Proveedor;
import com.gestorinventario.repository.CategoriaRepository;
import com.gestorinventario.repository.MovimientoRepository;
import com.gestorinventario.repository.ProductoRepository;
import com.gestorinventario.repository.ProveedorRepository;
import com.gestorinventario.service.ProductoService;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Metadata JPA y servicio real con repositorios mock: sin MySQL.
class ProveedorObligatorioTest {
    @Test
    void cifEsObligatorioYUnico() throws Exception {
        Column columna = Proveedor.class.getDeclaredField("cif").getAnnotation(Column.class);
        assertNotNull(columna);
        assertFalse(columna.nullable());
        assertTrue(columna.unique());
    }

    @Test
    void proveedorEsRelacionObligatoriaConColumnaNoNula() throws Exception {
        var campo = Producto.class.getDeclaredField("proveedor");
        ManyToOne relacion = campo.getAnnotation(ManyToOne.class);
        JoinColumn columna = campo.getAnnotation(JoinColumn.class);
        assertNotNull(relacion);
        assertNotNull(columna);
        assertFalse(relacion.optional());
        assertEquals("proveedor_id", columna.name());
        assertFalse(columna.nullable());
    }

    @Test
    void categoriaConservaSuClaveForaneaNoNula() throws Exception {
        var campo = Producto.class.getDeclaredField("categoria");
        assertNotNull(campo.getAnnotation(ManyToOne.class));
        JoinColumn columna = campo.getAnnotation(JoinColumn.class);
        assertNotNull(columna);
        assertEquals("categoria_id", columna.name());
        assertFalse(columna.nullable());
    }

    @Test
    void crearProductoConProveedorConservaRelacionesPreciosYStockInicial() {
        ProductoRepository productos = mock(ProductoRepository.class);
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        ProveedorRepository proveedores = mock(ProveedorRepository.class);
        ProductoService servicio = new ProductoService(productos, categorias, proveedores,
                mock(MovimientoRepository.class));
        Categoria categoria = new Categoria("Hardware", "Componentes");
        Proveedor proveedor = new Proveedor("Proveedor", "B12345678", null, null, null);
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria));
        when(proveedores.findById(2L)).thenReturn(Optional.of(proveedor));
        when(productos.save(any(Producto.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        ProductoRequest request = new ProductoRequest();
        request.setCodigo("P1");
        request.setNombre("Producto");
        request.setPrecioCompra(new BigDecimal("19.99"));
        request.setPrecioVenta(new BigDecimal("29.99"));
        request.setStockMinimo(1);
        request.setCategoriaId(1L);
        request.setProveedorId(2L);

        Producto producto = servicio.crearProducto(request);

        assertNull(producto.getId());
        assertSame(proveedor, producto.getProveedor());
        assertSame(categoria, producto.getCategoria());
        assertEquals("B12345678", producto.getProveedor().getCif());
        assertEquals(request.getPrecioCompra(), producto.getPrecioCompra());
        assertEquals(request.getPrecioVenta(), producto.getPrecioVenta());
        assertEquals(0, producto.getStock());
        assertTrue(producto.getActivo());
        verify(productos).save(producto);
    }
}
