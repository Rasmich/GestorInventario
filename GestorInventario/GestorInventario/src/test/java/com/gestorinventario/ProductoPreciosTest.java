package com.gestorinventario;

import com.gestorinventario.dto.ProductoRequest;
import com.gestorinventario.model.Producto;
import com.gestorinventario.model.Rol;
import com.gestorinventario.model.Usuario;
import com.gestorinventario.repository.UsuarioRepository;
import com.gestorinventario.service.JwtService;
import com.gestorinventario.service.ProductoService;
import jakarta.persistence.Column;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MVC, Bean Validation y seguridad JWT reales; sin servidor ni conexion a MySQL.
class ProductoPreciosTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ProductoService productos;
    private String token;

    @BeforeEach
    void preparar() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(JwtSecurityTest.TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Usuario admin = new Usuario("Admin", "admin", "hash-de-prueba", Rol.ADMIN);
        admin.setId(1L);
        when(context.getBean(UsuarioRepository.class).findByUsername("admin"))
                .thenReturn(Optional.of(admin));
        token = context.getBean(JwtService.class).generarToken(admin);
        productos = context.getBean(ProductoService.class);
        when(productos.crearProducto(any())).thenReturn(new Producto());
        when(productos.actualizarProducto(eq(1L), any())).thenReturn(new Producto());
    }

    @AfterEach
    void cerrar() {
        context.close();
    }

    static Stream<Arguments> precios() {
        return Stream.of("POST", "PUT").flatMap(metodo ->
                Stream.of("precioCompra", "precioVenta").flatMap(campo ->
                        Stream.of("0", "19", "19.9", "19.99", "19.999", "-0.01",
                                        "99999999.99", "100000000.00", "null")
                                .map(valor -> Arguments.of(metodo, campo, valor,
                                        switch (valor) {
                                            case "19.999", "-0.01", "100000000.00", "null" -> 400;
                                            default -> 200;
                                        }))));
    }

    @ParameterizedTest(name = "{0}: {1}={2} devuelve {3}")
    @MethodSource("precios")
    void validaAmbosPreciosEnPostYPut(String metodo, String campo, String valor, int esperado)
            throws Exception {
        String compra = campo.equals("precioCompra") ? valor : "1.00";
        String venta = campo.equals("precioVenta") ? valor : "1.00";
        String cuerpo = """
                {"codigo":"P1","nombre":"Producto","precioCompra":%s,"precioVenta":%s,
                 "stockMinimo":0,"categoriaId":1,"proveedorId":1}
                """.formatted(compra, venta);
        String ruta = metodo.equals("POST") ? "/api/productos" : "/api/productos/1";
        mvc.perform(request(HttpMethod.valueOf(metodo), ruta)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().is(esperado));

        if (esperado == 400) {
            // Los valores invalidos se rechazan antes de llamar al servicio, sin redondear.
            verifyNoInteractions(productos);
        } else {
            ArgumentCaptor<ProductoRequest> captor = ArgumentCaptor.forClass(ProductoRequest.class);
            if (metodo.equals("POST")) {
                verify(productos).crearProducto(captor.capture());
            } else {
                verify(productos).actualizarProducto(eq(1L), captor.capture());
            }
            assertEquals(new BigDecimal(compra), captor.getValue().getPrecioCompra());
            assertEquals(new BigDecimal(venta), captor.getValue().getPrecioVenta());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"precioCompra", "precioVenta"})
    void columnasTienenPrecisionYEscalaMonetarias(String campo) throws Exception {
        var atributo = Producto.class.getDeclaredField(campo);
        assertEquals(BigDecimal.class, atributo.getType());
        Column columna = atributo.getAnnotation(Column.class);
        assertNotNull(columna);
        assertEquals(10, columna.precision());
        assertEquals(2, columna.scale());
        assertFalse(columna.nullable());
    }
}
