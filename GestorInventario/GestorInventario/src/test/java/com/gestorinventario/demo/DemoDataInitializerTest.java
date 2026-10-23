package com.gestorinventario.demo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemoDataInitializerTest {
    @Test
    void rejectsOtherTargetsBeforeDatabaseCreation() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/real")
                .withProperty("spring.datasource.username", "demo_app");
        assertThrows(IllegalStateException.class, () -> DemoDatabaseGuard.validate(env));
        env.setProperty("spring.datasource.url", DemoDatabaseGuard.URL);
        assertDoesNotThrow(() -> DemoDatabaseGuard.validate(env));
        env.setProperty("spring.datasource.username", "root");
        assertThrows(IllegalStateException.class, () -> DemoDatabaseGuard.validate(env));
        env.setProperty("spring.datasource.username", "demo_app");
        env.setActiveProfiles("demo", "recuperacion");
        assertThrows(IllegalStateException.class, () -> DemoDatabaseGuard.validate(env));
    }

    @Test
    void existingInventoryIsNeverReset() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Long.class)).thenReturn(1L);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        new DemoDataInitializer(jdbc, encoder, new MockEnvironment()).run(null);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(encoder);
    }

    @Test
    void missingOrRepeatedPasswordsWriteNothing() {
        var jdbc = emptyDatabase();
        var env = new MockEnvironment();
        var seed = new DemoDataInitializer(jdbc, new BCryptPasswordEncoder(4), env);
        assertThrows(IllegalStateException.class, () -> seed.run(null));
        for (String key : keys()) env.setProperty(key, "same-test-password");
        assertThrows(IllegalStateException.class, () -> seed.run(null));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void createsHashedAccountsAndStockEntries() {
        var jdbc = emptyDatabase();
        var env = new MockEnvironment();
        String[] keys = keys();
        for (int i = 0; i < keys.length; i++) env.setProperty(keys[i], "fictitious-test-password-" + i);
        var encoder = new BCryptPasswordEncoder(4);
        new DemoDataInitializer(jdbc, encoder, env).run(null);
        var calls = org.mockito.Mockito.mockingDetails(jdbc).getInvocations();
        int users = 0;
        int movements = 0;
        for (var call : calls) {
            if (!call.getMethod().getName().equals("update")) continue;
            String sql = call.getArgument(0);
            if (sql.startsWith("INSERT INTO usuarios")) {
                String hash = call.getArgument(3);
                assertTrue(encoder.matches("fictitious-test-password-" + users, hash));
                assertEquals(users == 0 ? "ADMIN" : "EMPLEADO", call.getArgument(4));
                users++;
            }
            if (sql.startsWith("INSERT INTO movimientos")) movements++;
        }
        assertEquals(4, users);
        assertEquals(2, movements);
    }

    @Test
    void registersGuardInSpringWithoutBeanNameCollision() {
        var env = new MockEnvironment()
                .withProperty("spring.datasource.url", DemoDatabaseGuard.URL)
                .withProperty("spring.datasource.username", "demo_app");
        env.setActiveProfiles("demo");
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.setEnvironment(env);
            context.register(DemoDatabaseGuard.class);
            assertDoesNotThrow(context::refresh);
            assertTrue(context.containsBean("demoDatabaseValidationPostProcessor"));
        }
    }
    private JdbcTemplate emptyDatabase() {
        var jdbc = mock(JdbcTemplate.class);
        for (String table : new String[]{"usuarios", "categorias", "proveedores", "productos", "movimientos"}) {
            when(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).thenReturn(0L);
        }
        when(jdbc.queryForObject(startsWith("SELECT id"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        return jdbc;
    }

    private String[] keys() {
        return new String[]{"DEMO_ADMIN_PASSWORD", "DEMO_EMPLOYEE1_PASSWORD", "DEMO_EMPLOYEE2_PASSWORD", "DEMO_EMPLOYEE3_PASSWORD"};
    }
}