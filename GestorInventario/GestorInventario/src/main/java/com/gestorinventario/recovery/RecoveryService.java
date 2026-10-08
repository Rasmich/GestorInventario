package com.gestorinventario.recovery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Sin estereotipo Spring: disponible solo en el contexto de recuperacion. */
public class RecoveryService {
    // Lista cerrada: nunca se reciben identificadores arbitrarios ni hay usuario por defecto.
    public enum Destino {
        ADMIN(1L, "admin"), EMPLEADO(2L, "empleado");

        private final long id;
        private final String username;

        Destino(long id, String username) { this.id = id; this.username = username; }
        public long id() { return id; }
        public String username() { return username; }

        static Destino seleccionar(String entrada) {
            if (entrada != null) {
                for (Destino destino : values()) {
                    if (destino.username.equals(entrada.trim())) return destino;
                }
            }
            throw new IllegalArgumentException("Seleccion invalida");
        }
    }
    static final String IDENTIDAD = "SELECT username FROM usuarios WHERE id = ? FOR UPDATE";
    static final String ACTUALIZAR = "UPDATE usuarios SET password = ? WHERE id = ? AND username = ?";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder encoder;

    public RecoveryService(JdbcTemplate jdbc, TransactionTemplate transaction, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.encoder = encoder;
    }

    public void restablecer(Destino destino, char[] nueva, char[] confirmacion) {
        if (destino == null) throw new IllegalArgumentException("Seleccion obligatoria");
        PasswordRecovery.validar(nueva, confirmacion);
        transaction.executeWithoutResult(status -> {
            String username = jdbc.queryForObject(IDENTIDAD, String.class, destino.id());
            if (!destino.username().equals(username)) {
                throw new IllegalStateException("Identidad inesperada");
            }
            String hash = encoder.encode(java.nio.CharBuffer.wrap(nueva));
            if (jdbc.update(ACTUALIZAR, hash, destino.id(), destino.username()) != 1) {
                throw new IllegalStateException("Numero de registros inesperado");
            }
        });
    }
}
