package com.gestorinventario.config;

import com.gestorinventario.repository.UsuarioRepository;
import com.gestorinventario.security.JwtAuthenticationFilter;
import com.gestorinventario.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   UsuarioRepository usuarioRepository) throws Exception {
        // Solo se registra en la cadena de Spring Security, no como filtro servlet independiente.
        var jwtFilter = new JwtAuthenticationFilter(jwtService, usuarioRepository);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader("WWW-Authenticate", "Bearer");
                    response.getWriter().write(
                            "{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"JWT ausente o invalido\"}");
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/problem+json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"status\":403,\"title\":\"Forbidden\",\"detail\":\"No tienes permiso para realizar esta operacion\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/productos", "/api/productos/**",
                                "/api/categorias", "/api/categorias/**",
                                "/api/proveedores", "/api/proveedores/**",
                                "/api/movimientos").hasAnyRole("ADMIN", "EMPLEADO")
                        .requestMatchers(HttpMethod.POST, "/api/movimientos").hasAnyRole("ADMIN", "EMPLEADO")
                        .requestMatchers(HttpMethod.POST,
                                "/api/productos", "/api/productos/**",
                                "/api/categorias", "/api/categorias/**",
                                "/api/proveedores", "/api/proveedores/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/productos", "/api/productos/**",
                                "/api/categorias", "/api/categorias/**",
                                "/api/proveedores", "/api/proveedores/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/productos", "/api/productos/**",
                                "/api/categorias", "/api/categorias/**",
                                "/api/proveedores", "/api/proveedores/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
