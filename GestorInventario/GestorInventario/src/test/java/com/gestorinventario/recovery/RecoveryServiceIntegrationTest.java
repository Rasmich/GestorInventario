package com.gestorinventario.recovery;

import com.gestorinventario.config.PasswordConfig;
import java.nio.CharBuffer;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** JDBC y transaccion reales, solo base H2 desechable; nunca MySQL ni Flyway. */
class RecoveryServiceIntegrationTest {
    @ParameterizedTest @EnumSource(RecoveryService.Destino.class)
    void conservaOtrosUsuariosCamposProductosYMovimientos(RecoveryService.Destino destino) {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        char[] nueva = new char[16];
        Arrays.fill(nueva, 'a'); // Entrada sintetica de prueba, nunca una credencial real.
        char[] confirmacion = nueva.clone();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(db);
            jdbc.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY, username VARCHAR(255) UNIQUE, nombre VARCHAR(255), rol VARCHAR(20), activo BOOLEAN, password VARCHAR(255))");
            jdbc.execute("CREATE TABLE productos (id BIGINT PRIMARY KEY, nombre VARCHAR(255), stock INT)");
            jdbc.execute("CREATE TABLE movimientos (id BIGINT PRIMARY KEY, usuario_id BIGINT REFERENCES usuarios(id), producto_id BIGINT REFERENCES productos(id), cantidad INT)");
            var encoder = new PasswordConfig().passwordEncoder();
            String inicial = encoder.encode(CharBuffer.wrap(new char[] {'t','e','s','t','-','i','n','i','c','i','a','l'}));
            jdbc.update("INSERT INTO usuarios VALUES (1, 'admin', 'Administrador', 'ADMIN', TRUE, ?)", inicial);
            jdbc.update("INSERT INTO usuarios VALUES (2, 'empleado', 'Empleado', 'EMPLEADO', FALSE, ?)", inicial);
            jdbc.update("INSERT INTO usuarios VALUES (3, 'otro', 'Otra persona', 'EMPLEADO', TRUE, ?)", inicial);
            jdbc.update("INSERT INTO productos VALUES (1, 'Producto', 7)");
            jdbc.update("INSERT INTO movimientos VALUES (6, 2, 1, 3)");
            jdbc.update("INSERT INTO movimientos VALUES (7, 1, 1, 4)");
            var usuariosAntes = jdbc.queryForList("SELECT id, username, nombre, rol, activo FROM usuarios ORDER BY id");
            var productosAntes = jdbc.queryForList("SELECT * FROM productos ORDER BY id");
            var movimientosAntes = jdbc.queryForList("SELECT * FROM movimientos ORDER BY id");
            var service = new RecoveryService(jdbc,
                    new TransactionTemplate(new DataSourceTransactionManager(db)), encoder);
            service.restablecer(destino, nueva, confirmacion);
            assertEquals(usuariosAntes, jdbc.queryForList("SELECT id, username, nombre, rol, activo FROM usuarios ORDER BY id"));
            assertEquals(productosAntes, jdbc.queryForList("SELECT * FROM productos ORDER BY id"));
            assertEquals(movimientosAntes, jdbc.queryForList("SELECT * FROM movimientos ORDER BY id"));
            for (long id : new long[] {1, 2, 3}) {
                String actual = jdbc.queryForObject("SELECT password FROM usuarios WHERE id = ?", String.class, id);
                if (id == destino.id()) {
                    assertFalse(inicial.equals(actual));
                    assertTrue(encoder.matches(CharBuffer.wrap(nueva), actual));
                } else assertTrue(inicial.equals(actual), "Cambio una cuenta no seleccionada");
            }
        } finally {
            Arrays.fill(nueva, '\0');
            Arrays.fill(confirmacion, '\0');
            db.shutdown();
        }
    }
}
