package com.gestorinventario;

import com.gestorinventario.controller.ProductoController;
import com.gestorinventario.dto.ProductoRequest;
import com.gestorinventario.mapper.ResponseMapper;
import com.gestorinventario.model.*;
import com.gestorinventario.repository.*;
import com.gestorinventario.service.*;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;

/** JPA y servicios reales contra H2 en memoria; no carga Boot, MySQL ni Flyway. */
class StockAlertasTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ProductoRepository.class)
    @Import({ProductoService.class, MovimientoService.class})
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
            properties.setProperty("hibernate.show_sql", "false");
            factory.setJpaProperties(properties);
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @ParameterizedTest
    @CsvSource({"6,5,STOCK_CORRECTO", "5,5,STOCK_BAJO", "1,5,STOCK_BAJO",
            "0,5,SIN_STOCK", "0,0,SIN_STOCK", "1,0,STOCK_CORRECTO"})
    void calculaEstadoEnElDtoSinPersistirlo(int stock, int minimo, EstadoStock esperado) {
        Producto producto = new Producto();
        producto.setStock(stock);
        producto.setStockMinimo(minimo);
        var dto = ResponseMapper.toProducto(producto);
        assertEquals(esperado, dto.estadoStock());
        assertEquals(stock, dto.stock());
        assertEquals(minimo, dto.stockMinimo());
    }

    // Contexto nuevo por escenario: ninguna prueba comparte datos persistidos.
    private AnnotationConfigApplicationContext context;
    private ProductoRepository productos;
    private ProductoService service;
    private ProductoController controller;
    private MovimientoService movimientos;
    private Categoria categoria;
    private Proveedor proveedor;

    private void iniciar() {
        context = new AnnotationConfigApplicationContext(Config.class);
        productos = context.getBean(ProductoRepository.class);
        service = context.getBean(ProductoService.class);
        controller = new ProductoController(service);
        movimientos = context.getBean(MovimientoService.class);
        categoria = context.getBean(CategoriaRepository.class).save(new Categoria("Hardware", "Prueba"));
        proveedor = context.getBean(ProveedorRepository.class).save(
                new Proveedor("Proveedor", "CIF-TEST", null, null, null));
        context.getBean(UsuarioRepository.class).save(new Usuario("Empleado", "empleado", "no-usado", Rol.EMPLEADO));
    }

    @AfterEach void cerrar() {
        if (context != null) context.close();
    }

    private Producto producto(String codigo, int stock, int minimo, boolean activo) {
        var producto = new Producto(codigo, codigo, "Prueba", BigDecimal.ONE, BigDecimal.TEN,
                minimo, activo, categoria, proveedor);
        producto.setStock(stock);
        return productos.saveAndFlush(producto);
    }

    @Test void consultaRealExcluyeInactivosYStockCorrectoIncluyendoMinimoCero() {
        iniciar();
        var bajo = producto("BAJO", 2, 5, true);
        var vacio = producto("VACIO", 0, 0, true);
        producto("CORRECTO", 6, 5, true);
        producto("INACTIVO-BAJO", 1, 5, false);
        producto("INACTIVO-VACIO", 0, 5, false);
        var alertas = controller.obtenerProductosConStockBajo();
        assertEquals(2, alertas.size());
        assertEquals(java.util.Set.of(bajo.getId(), vacio.getId()),
                alertas.stream().map(dto -> dto.id()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(alertas.stream().allMatch(dto -> Boolean.TRUE.equals(dto.activo())));
        assertEquals(EstadoStock.STOCK_BAJO, alertas.stream().filter(dto -> dto.id().equals(bajo.getId())).findFirst().orElseThrow().estadoStock());
        assertEquals(EstadoStock.SIN_STOCK, alertas.stream().filter(dto -> dto.id().equals(vacio.getId())).findFirst().orElseThrow().estadoStock());
        assertEquals(5, productos.count());
    }

    @Test void entradasYSalidasActualizanAlertasTrasConfirmarCadaTransaccion() {
        iniciar();
        var producto = producto("P1", 0, 5, true);
        assertEquals(EstadoStock.SIN_STOCK, controller.obtenerProductosConStockBajo().getFirst().estadoStock());
        var entrada = movimientos.registrarMovimiento(producto.getId(), TipoMovimiento.ENTRADA, 8, null, "empleado");
        assertEquals(EstadoStock.STOCK_CORRECTO, ResponseMapper.toMovimiento(entrada).producto().estadoStock());
        assertTrue(controller.obtenerProductosConStockBajo().isEmpty());
        movimientos.registrarMovimiento(producto.getId(), TipoMovimiento.SALIDA, 3, null, "empleado");
        var alerta = controller.obtenerProductosConStockBajo().getFirst();
        assertEquals(5, alerta.stock());
        assertEquals(5, alerta.stockMinimo());
        assertEquals(EstadoStock.STOCK_BAJO, alerta.estadoStock());
        movimientos.registrarMovimiento(producto.getId(), TipoMovimiento.SALIDA, 5, null, "empleado");
        assertEquals(EstadoStock.SIN_STOCK, controller.obtenerProductosConStockBajo().getFirst().estadoStock());
        movimientos.registrarMovimiento(producto.getId(), TipoMovimiento.ENTRADA, 1, null, "empleado");
        assertEquals(EstadoStock.STOCK_BAJO, controller.obtenerProductosConStockBajo().getFirst().estadoStock());
        movimientos.registrarMovimiento(producto.getId(), TipoMovimiento.ENTRADA, 5, null, "empleado");
        assertTrue(controller.obtenerProductosConStockBajo().isEmpty());
        assertEquals(6, controller.obtenerProductoPorId(producto.getId()).getBody().stock());
        assertEquals(EstadoStock.STOCK_CORRECTO, controller.obtenerProductoPorId(producto.getId()).getBody().estadoStock());
        assertEquals(5, context.getBean(MovimientoRepository.class).count());
        assertEquals(1, productos.count());
    }

    @Test void desactivacionRetiraAlertaYConservaProductoYMovimientos() {
        iniciar();
        var producto = producto("P1", 0, 5, true);
        movimientos.registrarMovimiento(producto.getId(), TipoMovimiento.ENTRADA, 1, null, "empleado");
        assertEquals(1, controller.obtenerProductosConStockBajo().size());
        service.eliminarProducto(producto.getId());
        assertTrue(controller.obtenerProductosConStockBajo().isEmpty());
        var conservado = productos.findById(producto.getId()).orElseThrow();
        assertFalse(conservado.getActivo());
        assertEquals(1, conservado.getStock());
        assertEquals(1, context.getBean(MovimientoRepository.class).count());
    }

    @Test void cambiarMinimoRecalculaEstadoSinCambiarStock() {
        iniciar();
        var producto = producto("P1", 4, 2, true);
        assertTrue(controller.obtenerProductosConStockBajo().isEmpty());
        var request = new ProductoRequest();
        request.setCodigo("P1");
        request.setNombre("Producto");
        request.setPrecioCompra(BigDecimal.ONE);
        request.setPrecioVenta(BigDecimal.TEN);
        request.setStockMinimo(4);
        request.setCategoriaId(categoria.getId());
        request.setProveedorId(proveedor.getId());
        service.actualizarProducto(producto.getId(), request);
        assertEquals(EstadoStock.STOCK_BAJO, controller.obtenerProductosConStockBajo().getFirst().estadoStock());
        request.setStockMinimo(3);
        service.actualizarProducto(producto.getId(), request);
        assertTrue(controller.obtenerProductosConStockBajo().isEmpty());
        assertEquals(4, productos.findById(producto.getId()).orElseThrow().getStock());
    }
}
