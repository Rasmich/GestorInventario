package com.gestorinventario;

import com.gestorinventario.controller.*;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.ProductoService;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductoDuplicadoTest {
    private static final String MENSAJE =
            "Ya existe un producto con ese código. Introduce un código diferente.";

    private static String cuerpo(String codigo, Long categoria, Long proveedor) {
        return """
                {"codigo":"%s","nombre":"Producto","descripcion":"Conservar",
                 "precioCompra":2.50,"precioVenta":5.00,"stockMinimo":3,
                 "categoriaId":%d,"proveedorId":%d}
                """.formatted(codigo, categoria, proveedor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin clave", "Duplicate entry 'PER002' for key 'productos.codigo'",
            "Duplicate entry 'PER002' for key 'UK_generada'"})
    void duplicadoMySqlEnCreacionYEdicion(String texto) throws Exception {
        comprobar(new SQLException(texto, "23000", 1062), 409, MENSAJE);
    }

    @Test
    void duplicadoEncadenadoYH2() throws Exception {
        var batch = new SQLException("batch", "HY000", 0);
        batch.setNextException(new SQLException("sin clave", "23000", 1062));
        comprobar(batch, 409, MENSAJE);
        comprobar(new SQLException("sin clave", "23505", 23505), 409, MENSAJE);
    }

    @Test
    void otrosErroresConservanSuTratamiento() throws Exception {
        comprobar(new SQLException("relación", "23000", 1452), 409,
                "La operacion entra en conflicto con relaciones existentes");
        comprobar(new SQLException("obligatorio", "23000", 1048), 400,
                "Los datos incumplen las restricciones de almacenamiento");
        comprobar(new SQLException("longitud", "22001", 1406), 400,
                "Los datos incumplen las restricciones de almacenamiento");
        comprobar(new SQLException("integridad sin duplicado", "23000", 0), 409,
                "La operacion incumple una restriccion de integridad");
        comprobar(null, 409, "La operacion incumple una restriccion de integridad");
    }

    private void comprobar(SQLException sql, int estado, String mensaje) throws Exception {
        for (boolean alGuardar : new boolean[]{true, false}) {
            var productos = mock(ProductoRepository.class);
            var categorias = mock(CategoriaRepository.class);
            var proveedores = mock(ProveedorRepository.class);
            when(categorias.findById(1L)).thenReturn(Optional.of(new Categoria()));
            when(proveedores.findById(1L)).thenReturn(Optional.of(new Proveedor()));
            when(productos.findById(2L)).thenReturn(Optional.of(new Producto()));
            var error = new DataIntegrityViolationException("interno", sql);
            if (alGuardar) when(productos.save(any())).thenThrow(error);
            else {
                when(productos.save(any())).thenAnswer(i -> i.getArgument(0));
                doThrow(error).when(productos).flush();
            }
            when(productos.saveAndFlush(any())).thenThrow(error);
            var mvc = MockMvcBuilders.standaloneSetup(new ProductoController(new ProductoService(
                    productos, categorias, proveedores, mock(MovimientoRepository.class))))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("PER002", 1L, 1L)))
                    .andExpect(status().is(estado)).andExpect(jsonPath("$.detail").value(mensaje));
            mvc.perform(put("/api/productos/2").contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("PER002", 1L, 1L)))
                    .andExpect(status().is(estado)).andExpect(jsonPath("$.detail").value(mensaje));
        }
    }

    @Test
    void uniqueRealYRollbackDeEdicionConservanProductoYStock() throws Exception {
        // Reutiliza la configuración JPA/H2 temporal; no carga Boot ni MySQL.
        try (var contexto = new AnnotationConfigApplicationContext()) {
            contexto.register(CategoriaDuplicadaTest.Config.class, ProductoService.class);
            contexto.refresh();
            var categorias = contexto.getBean(CategoriaRepository.class);
            var proveedores = contexto.getBean(ProveedorRepository.class);
            var productos = contexto.getBean(ProductoRepository.class);
            var categoria = categorias.saveAndFlush(new Categoria("Categoría", ""));
            var proveedor = proveedores.saveAndFlush(new Proveedor("Proveedor", "CIF", null, null, null));
            var mvc = MockMvcBuilders.standaloneSetup(new ProductoController(contexto.getBean(ProductoService.class)))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            for (String codigo : new String[]{"PER002", "OTRO"}) {
                mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo(codigo, categoria.getId(), proveedor.getId())))
                        .andExpect(status().isOk());
            }
            var otro = productos.findAll().stream().filter(p -> p.getCodigo().equals("OTRO")).findFirst().orElseThrow();
            otro.setStock(17);
            productos.saveAndFlush(otro);
            Long version = productos.findById(otro.getId()).orElseThrow().getVersion();
            String duplicado = cuerpo("PER002", categoria.getId(), proveedor.getId());
            mvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(duplicado))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(MENSAJE));
            mvc.perform(put("/api/productos/" + otro.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(duplicado.replace("Conservar", "Cambiar")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(MENSAJE));
            assertEquals(2, productos.count());
            var conservado = productos.findById(otro.getId()).orElseThrow();
            assertEquals("OTRO", conservado.getCodigo());
            assertEquals("Conservar", conservado.getDescripcion());
            assertEquals(17, conservado.getStock());
            assertEquals(version, conservado.getVersion());
            mvc.perform(put("/api/productos/" + otro.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("OTRO", categoria.getId(), proveedor.getId())))
                    .andExpect(status().isOk());
        }
    }
}
