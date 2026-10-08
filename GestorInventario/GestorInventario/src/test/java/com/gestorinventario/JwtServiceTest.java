package com.gestorinventario;

import com.gestorinventario.model.Rol;
import com.gestorinventario.model.Usuario;
import com.gestorinventario.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    @org.junit.jupiter.api.BeforeEach
    void identidadPersistida() {
        usuario.setId(1L);
    }
    private static final byte[] CLAVE_TEST =
            "clave-ficticia-exclusiva-tests-32-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String SECRETO_TEST = Base64.getEncoder().encodeToString(CLAVE_TEST);
    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");
    private final Usuario usuario = new Usuario("Ana", "ana", "hash-no-debe-incluirse", Rol.EMPLEADO);
    private final JwtService jwt = new JwtService(SECRETO_TEST, 3600000,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void tokenIncluyeSubjectRolUidIatExpConFirmaHS256() {
        String token = jwt.generarToken(usuario);
        var firmado = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(CLAVE_TEST))
                .clock(() -> Date.from(AHORA)).build().parseSignedClaims(token);
        var claims = firmado.getPayload();
        assertEquals("HS256", firmado.getHeader().getAlgorithm());
        assertEquals("ana", claims.getSubject());
        assertEquals("EMPLEADO", claims.get("rol"));
        assertEquals(Date.from(AHORA), claims.getIssuedAt());
        assertEquals(Date.from(AHORA.plusSeconds(3600)), claims.getExpiration());
        assertEquals(1L, jwt.extraerUsuarioId(token));
        assertEquals(5, claims.size());
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("hash"));
        assertFalse(claims.containsKey("nombre"));
    }

    @Test
    void reconoceTokenValidoYExtraeUsernameYExpiracion() {
        String token = jwt.generarToken(usuario);
        assertTrue(jwt.esValido(token));
        assertFalse(jwt.estaExpirado(token));
        assertEquals("ana", jwt.extraerUsername(token));
        assertEquals(Date.from(AHORA.plusSeconds(3600)), jwt.extraerExpiracion(token));
    }

    @Test
    void rechazaPayloadManipulado() {
        String[] partes = jwt.generarToken(usuario).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        partes[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace("EMPLEADO", "ADMIN").getBytes(StandardCharsets.UTF_8));
        String alterado = String.join(".", partes);
        assertFalse(jwt.esValido(alterado));
        assertThrows(JwtException.class, () -> jwt.extraerUsername(alterado));
    }

    @Test
    void rechazaTokenExpiradoSinEsperar() {
        String token = jwt.generarToken(usuario);
        JwtService futuro = new JwtService(SECRETO_TEST, 3600000,
                Clock.fixed(AHORA.plusSeconds(3601), ZoneOffset.UTC));
        assertFalse(futuro.esValido(token));
        assertTrue(futuro.estaExpirado(token));
        assertThrows(JwtException.class, () -> futuro.extraerUsername(token));
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        byte[] otraClave = "otra-clave-ficticia-exclusiva-de-tests".getBytes(StandardCharsets.UTF_8);
        JwtService otro = new JwtService(Base64.getEncoder().encodeToString(otraClave),
                3600000, Clock.fixed(AHORA, ZoneOffset.UTC));
        assertFalse(jwt.esValido(otro.generarToken(usuario)));
    }

    @Test
    void rechazaTokensNulosVaciosOMalFormados() {
        assertFalse(jwt.esValido(null));
        assertFalse(jwt.esValido(""));
        assertFalse(jwt.esValido("no-es-un-jwt"));
    }

    @Test
    void rechazaClaveDebilYDuracionInvalida() {
        assertThrows(io.jsonwebtoken.security.WeakKeyException.class,
                () -> new JwtService(Base64.getEncoder().encodeToString(new byte[8]), 3600000));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRETO_TEST, 0));
    }

    @Test
    void rechazaClaimsObligatoriosAusentesAunqueLaFirmaSeaCorrecta() {
        String sinExpiracion = Jwts.builder().subject("ana").claim("rol", "EMPLEADO").claim("uid", 1L)
                .issuedAt(Date.from(AHORA)).signWith(Keys.hmacShaKeyFor(CLAVE_TEST), Jwts.SIG.HS256).compact();
        assertFalse(jwt.esValido(sinExpiracion));
    }

    @Test
    void rechazaTokensAntiguosSinIdentidadAunqueLaFirmaYFechasSeanCorrectas() {
        String antiguo = Jwts.builder().subject("ana").claim("rol", "EMPLEADO")
                .issuedAt(Date.from(AHORA)).expiration(Date.from(AHORA.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(CLAVE_TEST), Jwts.SIG.HS256).compact();
        assertFalse(jwt.esValido(antiguo));
        assertThrows(JwtException.class, () -> jwt.extraerUsuarioId(antiguo));
    }

    @Test
    void rechazaIdentidadInvalidaAunqueLaFirmaSeaCorrecta() {
        String invalido = Jwts.builder().subject("ana").claim("rol", "EMPLEADO").claim("uid", 0L)
                .issuedAt(Date.from(AHORA)).expiration(Date.from(AHORA.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(CLAVE_TEST), Jwts.SIG.HS256).compact();
        assertFalse(jwt.esValido(invalido));
    }

    @Test
    void noEmiteTokensParaUsuariosSinIdPersistido() {
        usuario.setId(null);
        assertThrows(IllegalArgumentException.class, () -> jwt.generarToken(usuario));
    }
}
