package com.gestorinventario.service;

import com.gestorinventario.dto.LoginRequest;
import com.gestorinventario.dto.LoginResponse;
import com.gestorinventario.model.Usuario;
import com.gestorinventario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(this::credencialesIncorrectas);

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw credencialesIncorrectas();
        }

        boolean coincide;
        try {
            coincide = passwordEncoder.matches(request.getPassword(), usuario.getPassword());
        } catch (IllegalArgumentException exception) {
            // Un valor que BCrypt no pueda comprobar tampoco autentica al usuario.
            throw credencialesIncorrectas();
        }
        if (!coincide) {
            throw credencialesIncorrectas();
        }

        String token = jwtService.generarToken(usuario);
        return new LoginResponse(usuario.getId(), usuario.getNombre(), usuario.getUsername(), usuario.getRol(), token);
    }

    private ResponseStatusException credencialesIncorrectas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
    }
}
