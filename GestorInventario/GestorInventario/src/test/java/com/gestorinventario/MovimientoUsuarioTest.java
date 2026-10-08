package com.gestorinventario;

import com.gestorinventario.controller.*;
import com.gestorinventario.dto.MovimientoRequest;
import com.gestorinventario.mapper.ResponseMapper;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.MovimientoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MovimientoUsuarioTest {
    private final MovimientoRepository movimientos = mock(MovimientoRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final MovimientoService servicio = new MovimientoService(movimientos, productos, usuarios);

    @ParameterizedTest
    @CsvSource({"ADMIN, ENTRADA, 12", "EMPLEADO, ENTRADA, 12",
            "ADMIN, SALIDA, 8", "EMPLEADO, SALIDA, 8"})
    void registraAutorAutenticadoIgnorandoAutorDelJson(Rol rol, TipoMovimiento tipo, int stockFinal)
            throws Exception {
        Usuario actor = new Usuario("Ana", "ana", "hash-secreto", rol);
        actor.setId(7L);
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setStock(10);
        when(usuarios.findByUsername("ana")).thenReturn(Optional.of(actor));
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(movimientos.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        var mvc = MockMvcBuilders.standaloneSetup(new MovimientoController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var authentication = UsernamePasswordAuthenticationToken.authenticated("ana", null, List.of());

        String respuesta = mvc.perform(post("/api/movimientos").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"productoId":1,"tipo":"%s","cantidad":2,
                         "usuarioId":99,"username":"otro","usuario":{"id":99,"username":"otro"}}
                        """.formatted(tipo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = JsonMapper.builder().build().readTree(respuesta);
        assertEquals(JsonMapper.builder().build().readTree("{\"id\":7,\"username\":\"ana\"}"), json.get("usuario"));
        assertFalse(respuesta.contains("password"));
        assertFalse(respuesta.contains("hash-secreto"));
        assertEquals(stockFinal, producto.getStock());
        verify(movimientos).save(argThat(movimiento -> movimiento.getUsuario() == actor
                && movimiento.getTipo() == tipo && movimiento.getProducto() == producto));
        verify(usuarios).findByUsername("ana");
        verifyNoMoreInteractions(usuarios);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actorInexistenteOInactivoNoModificaStockNiGuarda(boolean existe) {
        Producto producto = new Producto();
        producto.setStock(10);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        Usuario actor = new Usuario("Ana", "ana", "hash", Rol.ADMIN);
        actor.setActivo(false);
        when(usuarios.findByUsername("ana")).thenReturn(existe ? Optional.of(actor) : Optional.empty());
        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> servicio.registrarMovimiento(1L, tipo, 2, null, "ana"));
            assertEquals(401, error.getStatusCode().value());
        }
        assertEquals(10, producto.getStock());
        verify(productos, never()).save(any());
        verifyNoInteractions(movimientos);
    }

    @Test
    void historicoSinAutorYRelacionSinCascadas() throws Exception {
        Movimiento historico = new Movimiento(new Producto(), TipoMovimiento.ENTRADA, 1, null);
        assertNull(ResponseMapper.toMovimiento(historico).usuario());
        var campo = Movimiento.class.getDeclaredField("usuario");
        assertEquals(0, campo.getAnnotation(jakarta.persistence.ManyToOne.class).cascade().length);
        assertTrue(campo.getAnnotation(jakarta.persistence.ManyToOne.class).optional());
        assertTrue(campo.getAnnotation(jakarta.persistence.JoinColumn.class).nullable());
        assertEquals("usuario_id", campo.getAnnotation(jakarta.persistence.JoinColumn.class).name());
        assertEquals(Set.of("productoId", "tipo", "cantidad", "observaciones"),
                Arrays.stream(MovimientoRequest.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet()));
    }
}
