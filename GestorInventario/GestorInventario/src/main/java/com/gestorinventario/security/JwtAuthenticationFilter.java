package com.gestorinventario.security;

import com.gestorinventario.repository.UsuarioRepository;
import com.gestorinventario.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            String username = null;
            Long usuarioId = null;
            try {
                if (jwtService.esValido(token)) {
                    username = jwtService.extraerUsername(token);
                    usuarioId = jwtService.extraerUsuarioId(token);
                }
            } catch (JwtException | IllegalArgumentException exception) {
                // El contexto queda sin autenticar; la cadena devuelve 401 en rutas protegidas.
            }
            if (username != null) {
                Long idToken = usuarioId;
                usuarioRepository.findByUsername(username)
                        .filter(usuario -> idToken != null && idToken.equals(usuario.getId())
                                && Boolean.TRUE.equals(usuario.getActivo()) && usuario.getRol() != null)
                        .ifPresent(usuario -> {
                            // El rol procede de la base de datos, nunca de los claims del token.
                            var autoridad = new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name());
                            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                                    usuario.getUsername(), null, List.of(autoridad));
                            var context = SecurityContextHolder.createEmptyContext();
                            context.setAuthentication(authentication);
                            SecurityContextHolder.setContext(context);
                        });
            }
        }
        chain.doFilter(request, response);
    }
}
