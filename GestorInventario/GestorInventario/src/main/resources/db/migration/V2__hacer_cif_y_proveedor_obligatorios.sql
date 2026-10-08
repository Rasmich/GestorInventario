-- No modifica datos ni elimina las claves UNIQUE o FOREIGN KEY existentes.
ALTER TABLE proveedores MODIFY COLUMN cif VARCHAR(255) NOT NULL;
ALTER TABLE productos MODIFY COLUMN proveedor_id BIGINT NOT NULL;
