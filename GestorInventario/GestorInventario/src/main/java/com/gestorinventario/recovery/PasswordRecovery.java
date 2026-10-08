package com.gestorinventario.recovery;

import com.gestorinventario.config.PasswordConfig;
import java.io.Console;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Entrada independiente: nunca se registra como runner del servidor habitual. */
public class PasswordRecovery {
    public static void main(String[] args) {
        if (!perfilExplicito(args)) {
            System.err.println("Se requiere exclusivamente --spring.profiles.active=recuperacion");
            return;
        }
        Console console = System.console();
        if (console == null) {
            System.err.println("Se requiere una terminal real con entrada oculta; no se usara entrada visible.");
            return;
        }
        char[] nueva = null;
        char[] confirmacion = null;
        try {
            RecoveryService.Destino destino = solicitarDestino(console);
            nueva = console.readPassword("Nueva contrasena: ");
            confirmacion = console.readPassword("Confirmar contrasena: ");
            validar(nueva, confirmacion);
            SpringApplication app = new SpringApplication(RecoveryBeans.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setRegisterShutdownHook(false);
            // Sin auto-configuracion: no JPA, Flyway, inicializacion SQL ni servidor.
            // Desactivar logs impide registrar parametros JDBC incluso con configuracion debug.
            try (var context = app.run("--spring.profiles.active=recuperacion",
                    "--spring.main.web-application-type=none",
                    "--logging.level.root=OFF", "--logging.level.org.springframework=OFF",
                    "--logging.level.org.springframework.jdbc.core=OFF",
                    "--spring.main.banner-mode=off")) {
                context.getBean(RecoveryService.class).restablecer(destino, nueva, confirmacion);
            }
            console.printf("Contrasena actualizada para %s (id=%d).%n", destino.username(), destino.id());
        } catch (Exception exception) {
            // No imprimir excepciones: pueden contener parametros sensibles de JDBC.
            console.printf("Recuperacion cancelada o fallida. Verifica identidad, conexion y requisitos de entrada.%n");
        } finally {
            if (nueva != null) Arrays.fill(nueva, '\0');
            if (confirmacion != null) Arrays.fill(confirmacion, '\0');
        }
    }

    static RecoveryService.Destino solicitarDestino(Console console) {
        RecoveryService.Destino destino = RecoveryService.Destino.seleccionar(
                console.readLine("Usuario a recuperar (admin o empleado): "));
        console.printf("Usuario seleccionado: id=%d, username=%s.%n", destino.id(), destino.username());
        String esperado = "RECUPERAR " + destino.username();
        String confirmacion = console.readLine("Escribe " + esperado + " para confirmar: ");
        if (!esperado.equals(confirmacion)) {
            throw new IllegalArgumentException("Recuperacion no confirmada");
        }
        return destino;
    }

    static boolean perfilExplicito(String[] args) {
        return args.length == 1 && "--spring.profiles.active=recuperacion".equals(args[0]);
    }

    static void validar(char[] nueva, char[] confirmacion) {
        if (nueva == null || confirmacion == null || nueva.length < 8
                || CharBuffer.wrap(nueva).toString().isBlank()
                || !Arrays.equals(nueva, confirmacion)
                || StandardCharsets.UTF_8.encode(CharBuffer.wrap(nueva)).remaining() > 72) {
            throw new IllegalArgumentException("Entrada invalida");
        }
    }

    // Sin @Configuration/@Component: el component scan normal no descubre esta clase.
    @Profile("recuperacion")
    @Import(PasswordConfig.class)
    public static class RecoveryBeans {
        @Bean
        DataSource recoveryDataSource(Environment env) {
            var ds = new DriverManagerDataSource();
            ds.setUrl(env.getRequiredProperty("spring.datasource.url"));
            ds.setUsername(env.getRequiredProperty("spring.datasource.username"));
            ds.setPassword(env.getRequiredProperty("spring.datasource.password"));
            return ds;
        }

        @Bean
        RecoveryService recoveryService(DataSource ds, PasswordEncoder encoder) {
            return new RecoveryService(new JdbcTemplate(ds),
                    new TransactionTemplate(new DataSourceTransactionManager(ds)), encoder);
        }
    }
}
