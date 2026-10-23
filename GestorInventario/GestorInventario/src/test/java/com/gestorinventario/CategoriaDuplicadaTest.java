package com.gestorinventario;

import com.gestorinventario.controller.*;
import com.gestorinventario.model.Categoria;
import com.gestorinventario.repository.CategoriaRepository;
import com.gestorinventario.service.*;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoriaDuplicadaTest {
    private static final String MENSAJE =
            "Ya existe una categoría con ese nombre. Introduce un nombre diferente.";

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = CategoriaRepository.class)
    @Import({CategoriaService.class, EliminacionService.class})
    static class Config {
        @Bean(destroyMethod = "shutdown") EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("com.gestorinventario.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            var properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            factory.setJpaProperties(properties);
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @Test
    void restriccionRealRechazaCreacionYEdicionYRevierteLosDatos() throws Exception {
        // Solo H2 temporal: no conecta ni modifica la base de datos del usuario.
        try (var contexto = new AnnotationConfigApplicationContext(Config.class)) {
            var repositorio = contexto.getBean(CategoriaRepository.class);
            repositorio.saveAndFlush(new Categoria("1234", "Original"));
            var otra = repositorio.saveAndFlush(new Categoria("Otra", "Conservar"));
            var mvc = MockMvcBuilders.standaloneSetup(new CategoriaController(
                    contexto.getBean(CategoriaService.class)))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"1234\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(MENSAJE));
            mvc.perform(put("/api/categorias/" + otra.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"1234\",\"descripcion\":\"Cambiar\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(MENSAJE));
            assertEquals(2, repositorio.count());
            var conservada = repositorio.findById(otra.getId()).orElseThrow();
            assertEquals("Otra", conservada.getNombre());
            assertEquals("Conservar", conservada.getDescripcion());
            // Editar sin cambiar el nombre propio sigue permitido.
            mvc.perform(put("/api/categorias/" + otra.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Otra\",\"descripcion\":\"Actualizada\"}"))
                    .andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin texto del driver", "Duplicate entry '1234' for key 'categorias.nombre'",
            "Duplicate entry '1234' for key 'UK_generada_por_hibernate'"})
    void crearYEditarTraducenDuplicadosSinDependerDeClave(String texto) throws Exception {
        comprobar(new SQLException(texto, "23000", 1062), 409, MENSAJE);
    }

    @Test
    void reconoceExcepcionSqlEncadenada() throws Exception {
        var sql = new SQLException("batch", "HY000", 0);
        sql.setNextException(new SQLException("sin clave", "23000", 1062));
        comprobar(sql, 409, MENSAJE);
    }

    @Test
    void reconoceDuplicadoH2SinClave() throws Exception {
        comprobar(new SQLException("sin clave", "23505", 23505), 409, MENSAJE);
    }

    @Test
    void noTraduceOtrosErroresDeIntegridadComoNombreDuplicado() throws Exception {
        comprobar(new SQLException("relación", "23000", 1452), 409,
                "La operacion entra en conflicto con relaciones existentes");
        comprobar(new SQLException("longitud", "22001", 1406), 400,
                "Los datos incumplen las restricciones de almacenamiento");
    }

    private void comprobar(SQLException sql, int estado, String mensaje) throws Exception {
        // Cubrimos tanto INSERT inmediato como UPDATE diferido hasta flush.
        for (boolean alGuardar : new boolean[]{true, false}) {
            var repositorio = mock(CategoriaRepository.class);
            var existente = new Categoria("Otra", "Descripción");
            existente.setId(2L);
            when(repositorio.findById(2L)).thenReturn(Optional.of(existente));
            var error = new DataIntegrityViolationException("interno", sql);
            if (alGuardar) when(repositorio.save(any())).thenThrow(error);
            else {
                when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));
                doThrow(error).when(repositorio).flush();
            }
            var mvc = MockMvcBuilders.standaloneSetup(new CategoriaController(
                    new CategoriaService(repositorio, mock(EliminacionService.class))))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"1234\"}"))
                    .andExpect(status().is(estado)).andExpect(jsonPath("$.detail").value(mensaje));
            mvc.perform(put("/api/categorias/2").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"1234\"}"))
                    .andExpect(status().is(estado)).andExpect(jsonPath("$.detail").value(mensaje));
        }
    }
}
