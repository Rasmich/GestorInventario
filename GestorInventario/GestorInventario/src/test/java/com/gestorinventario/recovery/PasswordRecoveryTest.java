package com.gestorinventario.recovery;

import com.gestorinventario.config.PasswordConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.Console;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final TransactionStatus status = mock(TransactionStatus.class);
    private final RecoveryService service;
    private final char[] nueva = "EntradaSoloParaTest123".toCharArray();

    PasswordRecoveryTest() {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        service = new RecoveryService(jdbc, new TransactionTemplate(manager),
                new PasswordConfig().passwordEncoder());
    }

    @ParameterizedTest @EnumSource(RecoveryService.Destino.class)
    void actualizaSoloPasswordDelUsuarioSeleccionado(RecoveryService.Destino destino) {
        when(jdbc.queryForObject(RecoveryService.IDENTIDAD, String.class, destino.id())).thenReturn(destino.username());
        when(jdbc.update(eq(RecoveryService.ACTUALIZAR), anyString(), eq(destino.id()), eq(destino.username()))).thenReturn(1);
        service.restablecer(destino, nueva, nueva.clone());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject("SELECT username FROM usuarios WHERE id = ? FOR UPDATE", String.class, destino.id());
        verify(jdbc).update(eq("UPDATE usuarios SET password = ? WHERE id = ? AND username = ?"),
                hash.capture(), eq(destino.id()), eq(destino.username()));
        assertTrue(new PasswordConfig().passwordEncoder().matches(java.nio.CharBuffer.wrap(nueva), hash.getValue()));
        verifyNoMoreInteractions(jdbc);
        verify(manager).commit(status);
    }

    @ParameterizedTest @EnumSource(RecoveryService.Destino.class)
    void identidadDistintaNoActualiza(RecoveryService.Destino destino) {
        when(jdbc.queryForObject(RecoveryService.IDENTIDAD, String.class, destino.id())).thenReturn("otra-cuenta");
        assertThrows(IllegalStateException.class, () -> service.restablecer(destino, nueva, nueva.clone()));
        verify(jdbc).queryForObject(RecoveryService.IDENTIDAD, String.class, destino.id());
        verifyNoMoreInteractions(jdbc);
        verify(manager).rollback(status);
    }

    @ParameterizedTest @EnumSource(RecoveryService.Destino.class)
    void usuarioAusenteNoActualiza(RecoveryService.Destino destino) {
        when(jdbc.queryForObject(RecoveryService.IDENTIDAD, String.class, destino.id()))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThrows(EmptyResultDataAccessException.class, () -> service.restablecer(destino, nueva, nueva.clone()));
        verify(jdbc).queryForObject(RecoveryService.IDENTIDAD, String.class, destino.id());
        verifyNoMoreInteractions(jdbc);
        verify(manager).rollback(status);
    }

    @Test void cantidadInesperadaRevierteTransaccion() {
        when(jdbc.queryForObject(RecoveryService.IDENTIDAD, String.class, 2L)).thenReturn("empleado");
        assertThrows(IllegalStateException.class, () -> service.restablecer(RecoveryService.Destino.EMPLEADO, nueva, nueva.clone()));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }

    @Test void entradaInvalidaNoAccedeALaBase() {
        for (char[] entrada : new char[][] {null, new char[0], "corta".toCharArray(),
                " ".repeat(8).toCharArray(), "a".repeat(73).toCharArray(), "\u00e9".repeat(37).toCharArray()}) {
            assertThrows(IllegalArgumentException.class, () -> service.restablecer(RecoveryService.Destino.EMPLEADO, entrada, entrada));
        }
        assertThrows(IllegalArgumentException.class, () -> service.restablecer(RecoveryService.Destino.EMPLEADO, nueva, "distinta".toCharArray()));
        verifyNoInteractions(jdbc, manager);
    }

    @Test void noHayDestinoPorDefectoNiSeAdmitenUsuariosArbitrarios() {
        for (String entrada : new String[] {null, "", "1", "2", "otro", "ADMIN"}) {
            assertThrows(IllegalArgumentException.class, () -> RecoveryService.Destino.seleccionar(entrada));
        }
        assertThrows(IllegalArgumentException.class, () -> service.restablecer(null, nueva, nueva.clone()));
        verifyNoInteractions(jdbc, manager);
    }

    @ParameterizedTest @EnumSource(RecoveryService.Destino.class)
    void consolaExigeSeleccionYConfirmacionExplicitas(RecoveryService.Destino destino) {
        Console console = mock(Console.class);
        when(console.readLine("Usuario a recuperar (admin o empleado): ")).thenReturn(destino.username());
        when(console.readLine("Escribe RECUPERAR " + destino.username() + " para confirmar: "))
                .thenReturn("RECUPERAR " + destino.username());
        assertEquals(destino, PasswordRecovery.solicitarDestino(console));
        var orden = inOrder(console);
        orden.verify(console).readLine("Usuario a recuperar (admin o empleado): ");
        orden.verify(console).printf("Usuario seleccionado: id=%d, username=%s.%n", destino.id(), destino.username());
        orden.verify(console).readLine("Escribe RECUPERAR " + destino.username() + " para confirmar: ");
        verifyNoMoreInteractions(console);
        verifyNoInteractions(jdbc, manager);
    }

    @Test void confirmacionIncorrectaOCanceladaNoContinua() {
        for (String entrada : new String[] {null, "", "si", "RECUPERAR empleado"}) {
            Console console = mock(Console.class);
            when(console.readLine("Usuario a recuperar (admin o empleado): ")).thenReturn("admin");
            when(console.readLine("Escribe RECUPERAR admin para confirmar: ")).thenReturn(entrada);
            assertThrows(IllegalArgumentException.class, () -> PasswordRecovery.solicitarDestino(console));
            verify(console, never()).readPassword(anyString());
        }
        verifyNoInteractions(jdbc, manager);
    }

    @Test void aceptaLosLimitesDeLongitudEnCaracteresYBytesUtf8() {
        for (char[] entrada : new char[][] {"a".repeat(8).toCharArray(), "a".repeat(72).toCharArray(),
                "\u00e9".repeat(36).toCharArray()}) {
            assertDoesNotThrow(() -> PasswordRecovery.validar(entrada, entrada.clone()));
        }
        verifyNoInteractions(jdbc, manager);
    }

    @Test void perfilDebeSerExplicitoYNoAdmiteOtrosArgumentos() {
        assertFalse(PasswordRecovery.perfilExplicito(new String[0]));
        assertFalse(PasswordRecovery.perfilExplicito(new String[] {"--spring.profiles.active=dev"}));
        assertFalse(PasswordRecovery.perfilExplicito(new String[] {"--spring.profiles.active=recuperacion", "otro"}));
        assertTrue(PasswordRecovery.perfilExplicito(new String[] {"--spring.profiles.active=recuperacion"}));
    }

    @Test void contextoNormalNoDescubreLaHerramientaInclusoConPerfil() {
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true);
        var environment = new org.springframework.core.env.StandardEnvironment();
        environment.setActiveProfiles("recuperacion");
        scanner.setEnvironment(environment);
        assertTrue(scanner.findCandidateComponents("com.gestorinventario.recovery").isEmpty());
    }

    @Test void beansDeRecuperacionNoSeRegistranSinPerfil() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(PasswordRecovery.RecoveryBeans.class);
            context.refresh();
            assertTrue(context.getBeansOfType(RecoveryService.class).isEmpty());
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        }
    }

    @Test void perfilActivoCreaSoloContextoLocalSinConexionNiAutoconfiguracion() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("recuperacion");
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test", java.util.Map.of("spring.datasource.url", "jdbc:no-conectar:test",
                            "spring.datasource.username", "test", "spring.datasource.password", "")));
            context.register(PasswordRecovery.RecoveryBeans.class);
            context.refresh();
            assertEquals(1, context.getBeansOfType(RecoveryService.class).size());
            assertInstanceOf(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class,
                    context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class));
            assertTrue(context.getBeansOfType(org.flywaydb.core.Flyway.class).isEmpty());
            assertTrue(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
            assertFalse(context instanceof org.springframework.web.context.WebApplicationContext);
        }
    }
}
