package com.gestorinventario;

import com.gestorinventario.controller.*;
import com.gestorinventario.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasItem;

class MensajesValidacionTest {
    private final ProductoService productos = mock(ProductoService.class);
    private final CategoriaService categorias = mock(CategoriaService.class);
    private final ProveedorService proveedores = mock(ProveedorService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ProductoController(productos),
                        new CategoriaController(categorias), new ProveedorController(proveedores))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({
            "productos,uk_productos_codigo,Ya existe un producto con ese código. Introduce un código diferente.",
            "categorias,uk_categorias_nombre,Ya existe una categoría con ese nombre. Introduce un nombre diferente.",
            "proveedores,uk_proveedores_cif,Ya existe un proveedor con ese CIF. Revisa el CIF introducido."
    })
    void duplicadosEnCreacionYEdicion(String recurso, String clave, String mensaje) throws Exception {
        var error = new DataIntegrityViolationException("SQL interno", new SQLException(
                "Duplicate entry 'dato privado' for key 'tabla." + clave + "'", "23000", 1062));
        switch (recurso) {
            case "productos" -> {
                when(productos.crearProducto(any())).thenThrow(error);
                when(productos.actualizarProducto(eq(1L), any())).thenThrow(error);
            }
            case "categorias" -> {
                when(categorias.crearCategoria(any())).thenThrow(error);
                when(categorias.actualizarCategoria(eq(1L), any())).thenThrow(error);
            }
            case "proveedores" -> {
                when(proveedores.crearProveedor(any())).thenThrow(error);
                when(proveedores.actualizarProveedor(eq(1L), any())).thenThrow(error);
            }
        }
        String cuerpo = switch (recurso) {
            case "productos" -> """
                    {"codigo":"P1","nombre":"Producto","precioCompra":1,"precioVenta":2,
                     "stockMinimo":0,"categoriaId":1,"proveedorId":1}
                    """;
            case "categorias" -> "{\"nombre\":\"Categoría\"}";
            default -> "{\"nombre\":\"Proveedor\",\"cif\":\"A123\"}";
        };
        for (HttpMethod metodo : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT}) {
            mvc.perform(request(metodo, "/api/" + recurso + (metodo == HttpMethod.PUT ? "/1" : ""))
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(mensaje));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "uk_productos_codigo,Código",
            "uk_categorias_nombre,Nombre",
            "uk_proveedores_cif,CIF"
    })
    void reconoceRestriccionHibernateSinDependerDelTextoSql(String clave, String campo) {
        var sql = new SQLException("dato privado", "23505", 23505);
        var causa = new org.hibernate.exception.ConstraintViolationException("interno", sql,
                clave.toUpperCase(java.util.Locale.ROOT) + "_INDEX_1");
        var respuesta = new GlobalExceptionHandler().manejarIntegridad(
                new DataIntegrityViolationException("interno", causa));
        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertTrue(respuesta.getBody().getDetail().toLowerCase(java.util.Locale.ROOT)
                .contains(campo.toLowerCase(java.util.Locale.ROOT)));
        assertFalse(respuesta.getBody().getDetail().contains("privado"));
    }

    @Test
    void noConfundeElValorDuplicadoConElNombreDeLaRestriccion() {
        var respuesta = new GlobalExceptionHandler().manejarIntegridad(new DataIntegrityViolationException(
                "interno", new SQLException("Duplicate entry 'uk_productos_codigo' for key 'otra_clave'",
                "23000", 1062)));
        assertEquals("Ya existe un registro con esos datos. Revisa los campos que deben ser únicos.",
                respuesta.getBody().getDetail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void validacionesUsanCamposComprensiblesYMensajesEnEspanol(String metodo) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), "/api/productos" + (metodo.equals("PUT") ? "/1" : ""))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"codigo":"","nombre":"Producto","precioCompra":-1,"precioVenta":1.234,
                         "stockMinimo":-1,"categoriaId":null,"proveedorId":null}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Revisa los campos indicados."))
                .andExpect(jsonPath("$.errores", hasItem("Código del producto: Este campo es obligatorio.")))
                .andExpect(jsonPath("$.errores", hasItem("Precio de compra: El valor debe ser mayor o igual que 0.00.")))
                .andExpect(jsonPath("$.errores", hasItem("Precio de venta: Introduce un número con un máximo de 8 cifras enteras y 2 decimales.")))
                .andExpect(jsonPath("$.errores", hasItem("Stock mínimo: Introduce un número mayor o igual que cero.")))
                .andExpect(jsonPath("$.errores", hasItem("Categoría: Este campo es obligatorio.")))
                .andExpect(jsonPath("$.errores", hasItem("Proveedor: Este campo es obligatorio.")));
        verifyNoInteractions(productos);
    }

    @Test
    void conservaEstadoYMensajeDeErroresDePermisoYSesion() {
        var handler = new GlobalExceptionHandler();
        for (HttpStatus estado : new HttpStatus[]{HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN}) {
            var respuesta = handler.manejarNegocio(new ResponseStatusException(estado, "Mensaje de seguridad"));
            assertEquals(estado, respuesta.getStatusCode());
            assertEquals("Mensaje de seguridad", respuesta.getBody().getDetail());
        }
    }
}
