-- MySQL 8: comprobar todos los objetos existentes antes de ejecutar DDL.
-- No modifica definiciones incompatibles ni datos existentes.
SET @v3_columna_existe = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos'
      AND COLUMN_NAME = 'usuario_id'
);
SET @v3_columna_correcta = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos'
      AND COLUMN_NAME = 'usuario_id' AND DATA_TYPE = 'bigint'
      AND COLUMN_TYPE NOT LIKE '%unsigned%'
      AND COLUMN_TYPE NOT LIKE '%zerofill%'
      AND IS_NULLABLE = 'YES' AND COLUMN_DEFAULT IS NULL
      AND EXTRA = '' AND GENERATION_EXPRESSION = ''
);

SET @v3_indice_existe = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos'
      AND INDEX_NAME = 'idx_movimientos_usuario_id'
);
SET @v3_indice_correcto = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimientos'
      AND INDEX_NAME = 'idx_movimientos_usuario_id'
      AND COLUMN_NAME = 'usuario_id' AND SEQ_IN_INDEX = 1
      AND NON_UNIQUE = 1 AND INDEX_TYPE = 'BTREE'
      AND SUB_PART IS NULL AND COLLATION = 'A' AND IS_VISIBLE = 'YES'
);

-- Buscar tambien colisiones del nombre de la restriccion en el esquema.
SET @v3_fk_existe = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME = 'fk_movimientos_usuario'
);
SET @v3_fk_correcta = (
    SELECT COUNT(*)
    FROM information_schema.REFERENTIAL_CONSTRAINTS r
    JOIN information_schema.KEY_COLUMN_USAGE k
      ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA
     AND k.TABLE_NAME = r.TABLE_NAME
     AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
    WHERE r.CONSTRAINT_SCHEMA = DATABASE()
      AND r.TABLE_NAME = 'movimientos'
      AND r.CONSTRAINT_NAME = 'fk_movimientos_usuario'
      AND r.DELETE_RULE = 'RESTRICT'
      AND r.UPDATE_RULE IN ('RESTRICT', 'NO ACTION')
      AND k.COLUMN_NAME = 'usuario_id' AND k.ORDINAL_POSITION = 1
      AND k.REFERENCED_TABLE_SCHEMA = DATABASE()
      AND k.REFERENCED_TABLE_NAME = 'usuarios'
      AND k.REFERENCED_COLUMN_NAME = 'id'
      AND (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE k2
           WHERE k2.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA
             AND k2.TABLE_NAME = r.TABLE_NAME
             AND k2.CONSTRAINT_NAME = r.CONSTRAINT_NAME) = 1
);

-- SIGNAL no se puede preparar en MySQL 8. Una columna inexistente en una
-- tabla derivada controlada provoca un error explicito durante PREPARE.
-- Asi no se necesitan procedimientos ni permisos CREATE ROUTINE.
SET @v3_sql = CASE
    WHEN @v3_columna_existe > 0 AND @v3_columna_correcta <> 1 THEN
        'SELECT `ERROR_V3_usuario_id_definicion_incompatible` FROM (SELECT 1 AS ok) AS v3'
    WHEN @v3_indice_existe > 0
         AND (@v3_indice_existe <> 1 OR @v3_indice_correcto <> 1) THEN
        'SELECT `ERROR_V3_indice_definicion_incompatible` FROM (SELECT 1 AS ok) AS v3'
    WHEN @v3_fk_existe > 0
         AND (@v3_fk_existe <> 1 OR @v3_fk_correcta <> 1) THEN
        'SELECT `ERROR_V3_fk_definicion_incompatible` FROM (SELECT 1 AS ok) AS v3'
    ELSE 'SELECT 1'
END;
PREPARE v3_stmt FROM @v3_sql;
EXECUTE v3_stmt;
DEALLOCATE PREPARE v3_stmt;

SET @v3_sql = IF(@v3_columna_existe = 0,
    'ALTER TABLE movimientos ADD COLUMN usuario_id BIGINT NULL', 'SELECT 1');
PREPARE v3_stmt FROM @v3_sql;
EXECUTE v3_stmt;
DEALLOCATE PREPARE v3_stmt;

SET @v3_sql = IF(@v3_indice_existe = 0,
    'CREATE INDEX idx_movimientos_usuario_id ON movimientos (usuario_id)', 'SELECT 1');
PREPARE v3_stmt FROM @v3_sql;
EXECUTE v3_stmt;
DEALLOCATE PREPARE v3_stmt;

SET @v3_sql = IF(@v3_fk_existe = 0,
    'ALTER TABLE movimientos ADD CONSTRAINT fk_movimientos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE RESTRICT',
    'SELECT 1');
PREPARE v3_stmt FROM @v3_sql;
EXECUTE v3_stmt;
DEALLOCATE PREPARE v3_stmt;
