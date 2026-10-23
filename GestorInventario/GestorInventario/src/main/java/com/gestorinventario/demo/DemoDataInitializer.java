package com.gestorinventario.demo;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Environment env;

    public DemoDataInitializer(JdbcTemplate jdbc, PasswordEncoder encoder, Environment env) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.env = env;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Una sola instancia durante la primera carga. La transaccion evita cargas parciales.
        for (String table : List.of("usuarios", "categorias", "proveedores", "productos", "movimientos")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class) != 0) return;
        }
        String[] names = {"demo_admin", "demo_empleado1", "demo_empleado2", "demo_empleado3"};
        String[] keys = {"DEMO_ADMIN_PASSWORD", "DEMO_EMPLOYEE1_PASSWORD", "DEMO_EMPLOYEE2_PASSWORD", "DEMO_EMPLOYEE3_PASSWORD"};
        String[] hashes = new String[keys.length];
        var distinct = new HashSet<String>();
        for (int i = 0; i < keys.length; i++) {
            String password = env.getProperty(keys[i]);
            if (password == null || password.isBlank() || password.length() < 8
                    || password.getBytes(StandardCharsets.UTF_8).length > 72 || !distinct.add(password)) {
                throw new IllegalStateException("Demo: se requieren cuatro contrasenas distintas, de 8 caracteres y hasta 72 bytes UTF-8");
            }
            hashes[i] = encoder.encode(password);
        }
        for (int i = 0; i < names.length; i++) {
            jdbc.update("INSERT INTO usuarios(nombre, username, password, rol, activo) VALUES (?, ?, ?, ?, ?)",
                    i == 0 ? "Administrador de prueba" : "Empleado de prueba " + i,
                    names[i], hashes[i], i == 0 ? "ADMIN" : "EMPLEADO", true);
        }
        jdbc.update("INSERT INTO categorias(nombre, descripcion) VALUES (?, ?)", "Material de oficina DEMO", "Datos ficticios de prueba");
        jdbc.update("INSERT INTO categorias(nombre, descripcion) VALUES (?, ?)", "Accesorios DEMO", "Datos ficticios de prueba");
        jdbc.update("INSERT INTO proveedores(nombre, cif, email) VALUES (?, ?, ?)", "Proveedor ficticio DEMO", "DEMO-PROVEEDOR-001", "proveedor@example.invalid");
        Long office = jdbc.queryForObject("SELECT id FROM categorias WHERE nombre = ?", Long.class, "Material de oficina DEMO");
        Long accessories = jdbc.queryForObject("SELECT id FROM categorias WHERE nombre = ?", Long.class, "Accesorios DEMO");
        Long supplier = jdbc.queryForObject("SELECT id FROM proveedores WHERE cif = ?", Long.class, "DEMO-PROVEEDOR-001");
        Long admin = jdbc.queryForObject("SELECT id FROM usuarios WHERE username = ?", Long.class, "demo_admin");
        product("DEMO-001", "Cuaderno de prueba", 30, 5, office, supplier, admin);
        product("DEMO-002", "Boligrafo de prueba", 3, 10, office, supplier, admin);
        product("DEMO-003", "Cable de prueba", 0, 5, accessories, supplier, admin);
    }

    private void product(String code, String name, int stock, int minimum, Long category, Long supplier, Long admin) {
        jdbc.update("INSERT INTO productos(version, codigo, nombre, descripcion, precio_compra, precio_venta, stock, stock_minimo, activo, categoria_id, proveedor_id) VALUES (0, ?, ?, ?, 2.00, 4.00, ?, ?, ?, ?, ?)",
                code, name, "Producto ficticio para pruebas", stock, minimum, true, category, supplier);
        Long id = jdbc.queryForObject("SELECT id FROM productos WHERE codigo = ?", Long.class, code);
        if (stock > 0) {
            jdbc.update("INSERT INTO movimientos(producto_id, tipo, cantidad, fecha, observaciones, usuario_id) VALUES (?, 'ENTRADA', ?, CURRENT_TIMESTAMP, ?, ?)",
                    id, stock, "Carga inicial ficticia DEMO", admin);
        }
    }
}