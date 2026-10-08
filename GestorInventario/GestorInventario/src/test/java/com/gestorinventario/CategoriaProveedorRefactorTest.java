package com.gestorinventario;

import com.gestorinventario.controller.CategoriaController;
import com.gestorinventario.controller.ProveedorController;
import com.gestorinventario.controller.GlobalExceptionHandler;
import com.gestorinventario.model.Categoria;
import com.gestorinventario.model.Proveedor;
import com.gestorinventario.repository.CategoriaRepository;
import com.gestorinventario.repository.ProveedorRepository;
import com.gestorinventario.repository.ProductoRepository;
import com.gestorinventario.service.CategoriaService;
import com.gestorinventario.service.ProveedorService;
import com.gestorinventario.service.EliminacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.sql.SQLException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP simulado y repositorios mock: no arranca Spring Boot ni accede a MySQL.
class CategoriaProveedorRefactorTest {
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        EliminacionService eliminacion = new EliminacionService(categorias, proveedores, productos);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                new CategoriaController(new CategoriaService(categorias, eliminacion)),
                new ProveedorController(new ProveedorService(proveedores, eliminacion)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator).build();
        when(categorias.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proveedores.save(any(Proveedor.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void cerrar() {
        validator.close();
    }

    @Test
    void crearCategoriaValida() throws Exception {
        mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Hardware\",\"descripcion\":\"Componentes\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Categoria> captor = ArgumentCaptor.forClass(Categoria.class);
        verify(categorias).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals("Hardware", captor.getValue().getNombre());
        assertEquals("Componentes", captor.getValue().getDescripcion());
    }

    @Test
    void rechazarCategoriaSinNombreOVacio() throws Exception {
        for (String json : new String[] {"{}", "{\"nombre\":\"\"}", "{\"nombre\":\"   \"}"}) {
            mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verify(categorias, never()).save(any());
    }

    @Test
    void crearProveedorValido() throws Exception {
        mvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Proveedor A\",\"cif\":\"B12345678\","
                        + "\"telefono\":\"123456789\",\"email\":\"ventas@example.com\",\"direccion\":\"Calle A\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Proveedor> captor = ArgumentCaptor.forClass(Proveedor.class);
        verify(proveedores).save(captor.capture());
        Proveedor proveedor = captor.getValue();
        assertNull(proveedor.getId());
        assertEquals("Proveedor A", proveedor.getNombre());
        assertEquals("B12345678", proveedor.getCif());
        assertEquals("123456789", proveedor.getTelefono());
        assertEquals("ventas@example.com", proveedor.getEmail());
        assertEquals("Calle A", proveedor.getDireccion());
    }

    @Test
    void rechazarProveedorSinNombre() throws Exception {
        for (String json : new String[] {"{\"cif\":\"B123\"}", "{\"nombre\":\" \",\"cif\":\"B123\"}"}) {
            mvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verify(proveedores, never()).save(any());
    }

    @Test
    void rechazarProveedorSinCif() throws Exception {
        for (String json : new String[] {"{\"nombre\":\"A\"}", "{\"nombre\":\"A\",\"cif\":\"\"}"}) {
            mvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verify(proveedores, never()).save(any());
    }

    @Test
    void rechazarEmailIncorrecto() throws Exception {
        mvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\",\"cif\":\"B123\",\"email\":\"incorrecto\"}"))
                .andExpect(status().isBadRequest());
        verify(proveedores, never()).save(any());
    }

    @Test
    void emailOpcionalAdmiteAusenciaYVacio() throws Exception {
        for (String json : new String[] {"{\"nombre\":\"A\",\"cif\":\"B123\"}",
                "{\"nombre\":\"A\",\"cif\":\"B123\",\"email\":\"\"}"}) {
            mvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isOk());
        }
        verify(proveedores, times(2)).save(any());
    }

    @Test
    void postCategoriaNoPuedeAsignarIdExistente() throws Exception {
        mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":42,\"nombre\":\"Nueva\"}")).andExpect(status().isOk());
        verify(categorias).save(argThat(categoria -> categoria.getId() == null));
        verify(categorias, never()).findById(any());
    }

    @Test
    void postProveedorNoPuedeAsignarIdExistente() throws Exception {
        mvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":42,\"nombre\":\"Nuevo\",\"cif\":\"B123\"}"))
                .andExpect(status().isOk());
        verify(proveedores).save(argThat(proveedor -> proveedor.getId() == null));
        verify(proveedores, never()).findById(any());
    }

    @Test
    void putValidaAmbosDto() throws Exception {
        mvc.perform(put("/api/categorias/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/proveedores/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\",\"cif\":\"B123\",\"email\":\"mal\"}"))
                .andExpect(status().isBadRequest());
        verify(categorias, never()).save(any());
        verify(proveedores, never()).save(any());
    }

    @Test
    void putActualizaCamposYConservaIdDeRuta() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setId(1L);
        Proveedor proveedor = new Proveedor();
        proveedor.setId(2L);
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria));
        when(proveedores.findById(2L)).thenReturn(Optional.of(proveedor));
        mvc.perform(put("/api/categorias/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":99,\"nombre\":\"Nueva\",\"descripcion\":\"D\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/proveedores/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":99,\"nombre\":\"Nuevo\",\"cif\":\"B123\"}"))
                .andExpect(status().isOk());
        assertEquals(1L, categoria.getId());
        assertEquals("Nueva", categoria.getNombre());
        assertEquals("D", categoria.getDescripcion());
        assertEquals(2L, proveedor.getId());
        assertEquals("Nuevo", proveedor.getNombre());
        assertEquals("B123", proveedor.getCif());
    }

    @Test
    void eliminacionesConReferenciasSiguenDevolviendo409() throws Exception {
        when(categorias.findById(1L)).thenReturn(Optional.of(new Categoria()));
        when(proveedores.findById(2L)).thenReturn(Optional.of(new Proveedor()));
        when(productos.existsByCategoriaId(1L)).thenReturn(true);
        when(productos.existsByProveedorId(2L)).thenReturn(true);
        mvc.perform(delete("/api/categorias/1")).andExpect(status().isConflict());
        mvc.perform(delete("/api/proveedores/2")).andExpect(status().isConflict());
        verify(categorias, never()).delete(any(Categoria.class));
        verify(proveedores, never()).delete(any(Proveedor.class));
    }

    @Test
    void recursosInexistentesSiguenDevolviendo404() throws Exception {
        for (String ruta : new String[] {"/api/categorias/99", "/api/proveedores/99"}) {
            mvc.perform(get(ruta)).andExpect(status().isNotFound());
            mvc.perform(delete(ruta)).andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/categorias/99").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\"}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/proveedores/99").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\",\"cif\":\"B123\"}")).andExpect(status().isNotFound());
    }

    @Test
    void duplicadosSiguenDevolviendo409() throws Exception {
        var error = new DataIntegrityViolationException("duplicado",
                new SQLException("duplicado", "23000", 1062));
        when(categorias.save(any())).thenThrow(error);
        when(proveedores.save(any())).thenThrow(error);
        mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\",\"cif\":\"B123\"}")).andExpect(status().isConflict());
    }
}
