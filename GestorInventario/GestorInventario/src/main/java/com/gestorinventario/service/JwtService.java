package com.gestorinventario.service;

import com.gestorinventario.model.Rol;
import com.gestorinventario.model.Usuario;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey clave;
    private final long duracionMillis;
    private final Clock reloj;
    private final JwtParser parser;

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiration}") long duracionMillis) {
        this(secreto, duracionMillis, Clock.systemUTC());
    }

    // Reloj inyectable para comprobar expiracion sin esperas en las pruebas.
    public JwtService(String secreto, long duracionMillis, Clock reloj) {
        if (duracionMillis < 1000) {
            throw new IllegalArgumentException("jwt.expiration debe ser de al menos 1000 ms");
        }
        this.clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secreto));
        this.duracionMillis = duracionMillis;
        this.reloj = reloj;
        this.parser = Jwts.parser().verifyWith(clave)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .clock(() -> Date.from(reloj.instant())).build();
    }

    public String generarToken(Usuario usuario) {
        if (usuario.getId() == null || usuario.getId() <= 0) {
            throw new IllegalArgumentException("El usuario debe tener un ID persistido");
        }
        Instant ahora = reloj.instant();
        return Jwts.builder().subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name())
                .claim("uid", usuario.getId())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(duracionMillis)))
                .signWith(clave, Jwts.SIG.HS256).compact();
    }

    public String extraerUsername(String token) {
        return leerClaims(token).getSubject();
    }

    public Long extraerUsuarioId(String token) {
        return leerClaims(token).get("uid", Long.class);
    }

    public boolean esValido(String token) {
        try {
            leerClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Date extraerExpiracion(String token) {
        return leerClaims(token).getExpiration();
    }

    public boolean estaExpirado(String token) {
        try {
            leerClaims(token);
            return false;
        } catch (ExpiredJwtException exception) {
            return true;
        }
        // Un token manipulado o mal formado no se considera simplemente expirado.
    }

    private Claims leerClaims(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        Long usuarioId = claims.get("uid", Long.class);
        if (usuarioId == null || usuarioId <= 0) {
            throw new MalformedJwtException("Identidad JWT invalida");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getIssuedAt() == null || claims.getExpiration() == null
                || !claims.getExpiration().after(claims.getIssuedAt())
                || claims.getIssuedAt().after(Date.from(reloj.instant()))) {
            throw new MalformedJwtException("Claims JWT invalidos");
        }
        try {
            Rol.valueOf(claims.get("rol", String.class));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new MalformedJwtException("Rol JWT invalido");
        }
        return claims;
    }
}
