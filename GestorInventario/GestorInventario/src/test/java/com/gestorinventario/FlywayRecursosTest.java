package com.gestorinventario;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

// Solo lee recursos del classpath: no crea contexto Spring ni ejecuta SQL.
class FlywayRecursosTest {
    private String recurso(String nombre) throws IOException {
        try (var entrada = getClass().getResourceAsStream("/" + nombre)) {
            assertNotNull(entrada, "Recurso ausente: " + nombre);
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String sql(String nombre) throws IOException {
        return recurso("db/migration/" + nombre).replaceAll("(?m)--[^\\r\\n]*", "").trim();
    }

    @Test
    void v1ContieneExactamenteLasCincoTablasConIdsGenerados() throws Exception {
        String v1 = sql("V1__esquema_inicial.sql");
        var tablas = Pattern.compile("CREATE TABLE (\\w+)").matcher(v1)
                .results().map(resultado -> resultado.group(1)).toList();
        assertEquals(List.of("categorias", "proveedores", "productos", "movimientos", "usuarios"), tablas);
        assertEquals(5, Pattern.compile("id BIGINT NOT NULL AUTO_INCREMENT,").matcher(v1).results().count());
        assertEquals(5, Pattern.compile("PRIMARY KEY \\(id\\)").matcher(v1).results().count());
        assertFalse(Pattern.compile("AUTO_INCREMENT\\s*=").matcher(v1).find());
    }

    @Test
    void v1ConservaNulabilidadBaseYTiposDelModelo() throws Exception {
        String v1 = sql("V1__esquema_inicial.sql");
        assertTrue(v1.contains("cif VARCHAR(255) NULL,"));
        assertTrue(v1.contains("proveedor_id BIGINT NULL,"));
        assertTrue(v1.contains("categoria_id BIGINT NOT NULL,"));
        assertTrue(v1.contains("precio_compra DECIMAL(10,2) NOT NULL,"));
        assertTrue(v1.contains("precio_venta DECIMAL(10,2) NOT NULL,"));
        assertTrue(v1.contains("version BIGINT NOT NULL DEFAULT 0,"));
        assertTrue(v1.contains("tipo ENUM('ENTRADA','SALIDA') NOT NULL,"));
        assertTrue(v1.contains("rol ENUM('ADMIN','EMPLEADO') NOT NULL,"));
    }

    @Test
    void v1DeclaraClavesEstablesSinBorradoEnCascada() throws Exception {
        String v1 = sql("V1__esquema_inicial.sql");
        for (String clave : List.of("uk_categorias_nombre", "uk_proveedores_cif", "uk_productos_codigo",
                "uk_usuarios_username", "fk_productos_categoria", "fk_productos_proveedor",
                "fk_movimientos_producto")) {
            assertTrue(v1.contains("CONSTRAINT " + clave + " "));
        }
        assertFalse(v1.toUpperCase().contains("CASCADE"));
    }

    @Test
    void v2SoloHaceObligatoriasLasDosColumnasSinCambiarTipos() throws Exception {
        String v2 = sql("V2__hacer_cif_y_proveedor_obligatorios.sql").replaceAll("\\s+", " ");
        assertEquals("ALTER TABLE proveedores MODIFY COLUMN cif VARCHAR(255) NOT NULL; "
                + "ALTER TABLE productos MODIFY COLUMN proveedor_id BIGINT NOT NULL;", v2);
    }

    @Test
    void v3SoloAnadeRelacionNullableSinModificarHistorial() throws Exception {
        String v3 = v3();
        // Examinar tanto el SQL directo como los literales de SQL dinamico.
        // UPDATE_RULE es metadato; no equivale a una sentencia UPDATE.
        assertFalse(Pattern.compile("\\b(INSERT|UPDATE|DELETE|REPLACE)\\s+(INTO\\s+|FROM\\s+)?[A-Z_][A-Z_0-9]*\\s",
                Pattern.CASE_INSENSITIVE).matcher(v3).find());
        assertFalse(Pattern.compile("\\b(DROP|TRUNCATE|MODIFY|CHANGE|RENAME|CASCADE)\\b").matcher(v3).find());
        assertFalse(v3.contains("NOT NULL"));
        assertFalse(v3.contains("FLYWAY_SCHEMA_HISTORY"));
        assertFalse(v3.contains("FOREIGN_KEY_CHECKS"));
        var ddl = Pattern.compile("'((?:ALTER TABLE|CREATE INDEX)[^']*)'").matcher(v3)
                .results().map(resultado -> resultado.group(1)).toList();
        assertEquals(List.of(
                "ALTER TABLE MOVIMIENTOS ADD COLUMN USUARIO_ID BIGINT NULL",
                "CREATE INDEX IDX_MOVIMIENTOS_USUARIO_ID ON MOVIMIENTOS (USUARIO_ID)",
                "ALTER TABLE MOVIMIENTOS ADD CONSTRAINT FK_MOVIMIENTOS_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID) ON DELETE RESTRICT"), ddl);
        // Ningun DDL debe ejecutarse directamente al margen de los IF.
        String directo = v3.replaceAll("'(?:''|[^'])*'", "''");
        assertFalse(Pattern.compile("\\b(ALTER|CREATE)\\s").matcher(directo).find());
    }

    private String v3() throws IOException {
        return sql("V3__anadir_usuario_a_movimientos.sql").replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private String consultaV3(String variable) throws IOException {
        var consulta = Pattern.compile("SET " + Pattern.quote(variable) + " = \\((.*?)\\);").matcher(v3());
        assertTrue(consulta.find(), "Falta comprobacion independiente: " + variable);
        return consulta.group(1);
    }

    private void contiene(String texto, String... requisitos) {
        for (String requisito : requisitos) {
            assertTrue(texto.contains(requisito), "Falta requisito: " + requisito);
        }
    }

    @Test
    void v3CompruebaExistenciaPorSeparadoEnElEsquemaActual() throws Exception {
        contiene(consultaV3("@V3_COLUMNA_EXISTE"), "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_SCHEMA = DATABASE()", "TABLE_NAME = 'MOVIMIENTOS'", "COLUMN_NAME = 'USUARIO_ID'");
        contiene(consultaV3("@V3_INDICE_EXISTE"), "INFORMATION_SCHEMA.STATISTICS",
                "TABLE_SCHEMA = DATABASE()", "TABLE_NAME = 'MOVIMIENTOS'", "INDEX_NAME = 'IDX_MOVIMIENTOS_USUARIO_ID'");
        contiene(consultaV3("@V3_FK_EXISTE"), "INFORMATION_SCHEMA.TABLE_CONSTRAINTS",
                "CONSTRAINT_SCHEMA = DATABASE()", "CONSTRAINT_NAME = 'FK_MOVIMIENTOS_USUARIO'");
    }

    @Test
    void v3ValidaDefinicionesCompletasDeLosObjetosExistentes() throws Exception {
        contiene(consultaV3("@V3_COLUMNA_CORRECTA"), "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_SCHEMA = DATABASE()", "TABLE_NAME = 'MOVIMIENTOS'", "COLUMN_NAME = 'USUARIO_ID'",
                "DATA_TYPE = 'BIGINT'", "COLUMN_TYPE NOT LIKE '%UNSIGNED%'", "COLUMN_TYPE NOT LIKE '%ZEROFILL%'",
                "IS_NULLABLE = 'YES'", "COLUMN_DEFAULT IS NULL", "EXTRA = ''", "GENERATION_EXPRESSION = ''");
        contiene(consultaV3("@V3_INDICE_CORRECTO"), "INFORMATION_SCHEMA.STATISTICS",
                "TABLE_SCHEMA = DATABASE()", "TABLE_NAME = 'MOVIMIENTOS'", "INDEX_NAME = 'IDX_MOVIMIENTOS_USUARIO_ID'",
                "COLUMN_NAME = 'USUARIO_ID'", "SEQ_IN_INDEX = 1", "NON_UNIQUE = 1", "INDEX_TYPE = 'BTREE'",
                "SUB_PART IS NULL", "COLLATION = 'A'", "IS_VISIBLE = 'YES'");
        contiene(consultaV3("@V3_FK_CORRECTA"), "INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS",
                "INFORMATION_SCHEMA.KEY_COLUMN_USAGE", "R.CONSTRAINT_SCHEMA = DATABASE()",
                "R.TABLE_NAME = 'MOVIMIENTOS'", "R.CONSTRAINT_NAME = 'FK_MOVIMIENTOS_USUARIO'",
                "R.DELETE_RULE = 'RESTRICT'", "R.UPDATE_RULE IN ('RESTRICT', 'NO ACTION')",
                "K.COLUMN_NAME = 'USUARIO_ID'", "K.ORDINAL_POSITION = 1", "K.REFERENCED_TABLE_SCHEMA = DATABASE()",
                "K.REFERENCED_TABLE_NAME = 'USUARIOS'", "K.REFERENCED_COLUMN_NAME = 'ID'",
                "K.CONSTRAINT_SCHEMA = R.CONSTRAINT_SCHEMA", "K.TABLE_NAME = R.TABLE_NAME",
                "K.CONSTRAINT_NAME = R.CONSTRAINT_NAME", "SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE K2",
                "K2.CONSTRAINT_SCHEMA = R.CONSTRAINT_SCHEMA", "K2.TABLE_NAME = R.TABLE_NAME",
                "K2.CONSTRAINT_NAME = R.CONSTRAINT_NAME) = 1");
    }

    @Test
    void v3DetieneIncompatibilidadesAntesDeCualquierCreacion() throws Exception {
        String v3 = v3();
        var preflight = Pattern.compile("SET @V3_SQL = CASE (.*?) END; PREPARE V3_STMT FROM @V3_SQL; EXECUTE V3_STMT; DEALLOCATE PREPARE V3_STMT;")
                .matcher(v3);
        assertTrue(preflight.find(), "La validacion debe ejecutarse antes del DDL");
        String comprobacion = preflight.group(1);
        contiene(comprobacion,
                "WHEN @V3_COLUMNA_EXISTE > 0 AND @V3_COLUMNA_CORRECTA <> 1 THEN",
                "WHEN @V3_INDICE_EXISTE > 0 AND (@V3_INDICE_EXISTE <> 1 OR @V3_INDICE_CORRECTO <> 1) THEN",
                "WHEN @V3_FK_EXISTE > 0 AND (@V3_FK_EXISTE <> 1 OR @V3_FK_CORRECTA <> 1) THEN",
                "ELSE 'SELECT 1'");
        // Cada rama incompatible provoca un error: su columna no existe en la tabla derivada.
        assertEquals(3, Pattern.compile("THEN 'SELECT `ERROR_V3_[A-Z_0-9]+` FROM \\(SELECT 1 AS OK\\) AS V3'")
                .matcher(comprobacion).results().count());
        assertTrue(preflight.end() < v3.indexOf("SET @V3_SQL = IF("));
    }

    @Test
    void v3CreaSoloObjetosAusentesYLiberaCadaSentenciaPreparada() throws Exception {
        String v3 = v3();
        var ramas = Pattern.compile("SET @V3_SQL = IF\\((@V3_[A-Z_]+) = 0, '([^']+)', 'SELECT 1'\\); "
                + "PREPARE V3_STMT FROM @V3_SQL; EXECUTE V3_STMT; DEALLOCATE PREPARE V3_STMT;")
                .matcher(v3).results().toList();
        assertEquals(3, ramas.size(), "Cada objeto necesita su propio IF con alternativa inocua");
        assertEquals(List.of("@V3_COLUMNA_EXISTE", "@V3_INDICE_EXISTE", "@V3_FK_EXISTE"),
                ramas.stream().map(rama -> rama.group(1)).toList());
        assertTrue(ramas.get(0).group(2).startsWith("ALTER TABLE MOVIMIENTOS ADD COLUMN USUARIO_ID "));
        assertTrue(ramas.get(1).group(2).startsWith("CREATE INDEX IDX_MOVIMIENTOS_USUARIO_ID "));
        assertTrue(ramas.get(2).group(2).startsWith("ALTER TABLE MOVIMIENTOS ADD CONSTRAINT FK_MOVIMIENTOS_USUARIO "));
        for (String comando : List.of("PREPARE V3_STMT FROM", "EXECUTE V3_STMT;", "DEALLOCATE PREPARE V3_STMT;")) {
            assertEquals(4, Pattern.compile(Pattern.quote(comando)).matcher(v3).results().count());
        }
    }

    @Test
    void hibernateValidaYNoHayBaselineAutomatico() throws Exception {
        Properties propiedades = new Properties();
        try (var entrada = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(entrada);
            propiedades.load(entrada);
        }
        assertEquals("validate", propiedades.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertFalse(Boolean.parseBoolean(propiedades.getProperty("spring.flyway.baseline-on-migrate", "false")));
        assertFalse(propiedades.containsKey("spring.flyway.baseline-on-migrate"));
    }
}
