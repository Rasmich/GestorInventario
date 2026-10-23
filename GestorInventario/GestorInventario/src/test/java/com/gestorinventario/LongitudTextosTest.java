package com.gestorinventario;

import com.gestorinventario.controller.GlobalExceptionHandler;
import com.gestorinventario.controller.MovimientoController;
import com.gestorinventario.dto.*;
import com.gestorinventario.model.Movimiento;
import com.gestorinventario.service.MovimientoService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LongitudTextosTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void cerrar() { FACTORY.close(); }

    static Stream<Arguments> campos() {
        return Stream.of(
                Arguments.of(CategoriaRequest.class, "nombre", false),
                Arguments.of(CategoriaRequest.class, "descripcion", true),
                Arguments.of(ProveedorRequest.class, "nombre", false),
                Arguments.of(ProveedorRequest.class, "cif", false),
                Arguments.of(ProveedorRequest.class, "telefono", true),
                Arguments.of(ProveedorRequest.class, "email", true),
                Arguments.of(ProveedorRequest.class, "direccion", true),
                Arguments.of(ProductoRequest.class, "codigo", false),
                Arguments.of(ProductoRequest.class, "nombre", false),
                Arguments.of(ProductoRequest.class, "descripcion", true),
                Arguments.of(MovimientoRequest.class, "observaciones", true));
    }

    // Email válido también en el límite: no confundir @Email con @Size.
    private static String texto(String campo, int longitud) {
        if (!campo.equals("email")) return "á".repeat(longitud);
        return "a@" + "b".repeat(63) + "." + "c".repeat(63) + "."
                + "d".repeat(63) + "." + "e".repeat(longitud - 194);
    }

    @ParameterizedTest
    @MethodSource("campos")
    <T> void acepta255YRechaza256(Class<T> dto, String campo, boolean opcional) {
        assertTrue(VALIDATOR.validateValue(dto, campo, texto(campo, 255)).isEmpty());
        var errores = VALIDATOR.validateValue(dto, campo, texto(campo, 256));
        assertTrue(errores.stream().anyMatch(error -> error.getMessage().contains("255 caracteres")));
        if (opcional) {
            assertTrue(VALIDATOR.validateValue(dto, campo, null).isEmpty());
            assertTrue(VALIDATOR.validateValue(dto, campo, "").isEmpty());
        } else {
            assertFalse(VALIDATOR.validateValue(dto, campo, null).isEmpty());
            assertFalse(VALIDATOR.validateValue(dto, campo, "").isEmpty());
            assertFalse(VALIDATOR.validateValue(dto, campo, "   ").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {255, 256})
    void validaObservacionesAntesDeInvocarServicio(int longitud) throws Exception {
        var servicio = mock(MovimientoService.class);
        when(servicio.registrarMovimiento(any(), any(), any(), any(), any()))
                .thenReturn(new Movimiento());
        var mvc = MockMvcBuilders.standaloneSetup(new MovimientoController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var actor = UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of());
        var resultado = mvc.perform(post("/api/movimientos").principal(actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"productoId":1,"tipo":"ENTRADA","cantidad":1,"observaciones":"%s"}
                        """.formatted("a".repeat(longitud))));
        resultado.andExpect(status().is(longitud == 255 ? 200 : 400));
        if (longitud == 256) {
            resultado.andExpect(jsonPath("$.errores[0]")
                    .value("Observaciones: No puede superar los 255 caracteres."));
            verifyNoInteractions(servicio);
        } else {
            verify(servicio).registrarMovimiento(eq(1L), any(), eq(1), eq("a".repeat(255)), eq("admin"));
        }
    }
}
