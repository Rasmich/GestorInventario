package com.gestorinventario.service;

import com.gestorinventario.dto.UsuarioRequest;
import com.gestorinventario.dto.UsuarioActualizacionRequest;
import com.gestorinventario.model.Rol;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.domain.Sort;
import com.gestorinventario.model.Usuario;
import com.gestorinventario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario crearUsuario(UsuarioRequest request) {
        if (usuarioRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El username ya existe");
        }
        Usuario usuario = new Usuario(
                request.getNombre(),
                request.getUsername(),
                codificarPassword(request.getPassword()),
                request.getRol());
        // El constructor conserva activo=true y el ID generado por MySQL.
        return usuarioRepository.saveAndFlush(usuario);
    }

    public List<Usuario> listarUsuarios() {
        return usuarioRepository.findAll(Sort.by("id"));
    }

    public Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id).orElseThrow(this::noEncontrado);
    }

    @Transactional
    public Usuario actualizarUsuario(Long id, UsuarioActualizacionRequest request, String actor) {
        List<Usuario> administradores = bloquearAdministradores(actor);
        Usuario usuario = usuarioRepository.findByIdForUpdate(id).orElseThrow(this::noEncontrado);
        if (usuarioRepository.existsByUsernameAndIdNot(request.username(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El username ya existe");
        }
        if (request.rol() != Rol.ADMIN) {
            protegerUltimoAdministrador(usuario, administradores);
        }
        usuario.setNombre(request.nombre());
        usuario.setUsername(request.username());
        usuario.setRol(request.rol());
        return usuarioRepository.saveAndFlush(usuario);
    }

    @Transactional
    public Usuario cambiarEstado(Long id, boolean activo, String actor) {
        List<Usuario> administradores = bloquearAdministradores(actor);
        Usuario usuario = usuarioRepository.findByIdForUpdate(id).orElseThrow(this::noEncontrado);
        if (!activo) {
            if (usuario.getUsername().equals(actor)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes desactivarte a ti mismo");
            }
            protegerUltimoAdministrador(usuario, administradores);
        }
        usuario.setActivo(activo);
        return usuarioRepository.saveAndFlush(usuario);
    }

    @Transactional
    public Usuario cambiarPassword(Long id, String password, String actor) {
        bloquearAdministradores(actor);
        Usuario usuario = usuarioRepository.findByIdForUpdate(id).orElseThrow(this::noEncontrado);
        usuario.setPassword(codificarPassword(password));
        return usuarioRepository.saveAndFlush(usuario);
    }

    private List<Usuario> bloquearAdministradores(String actor) {
        List<Usuario> administradores = usuarioRepository.findAdministradoresActivosForUpdate();
        // Revalidar al actor dentro de la transaccion si su rol/estado cambio desde el filtro JWT.
        if (administradores.stream().noneMatch(usuario -> usuario.getUsername().equals(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere un administrador activo");
        }
        return administradores;
    }

    private void protegerUltimoAdministrador(Usuario usuario, List<Usuario> administradores) {
        if (usuario.getRol() == Rol.ADMIN && Boolean.TRUE.equals(usuario.getActivo())
                && administradores.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe existir al menos un administrador activo");
        }
    }

    private String codificarPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contrasena requiere al menos 8 caracteres y como maximo 72 bytes UTF-8");
        }
        return passwordEncoder.encode(password);
    }

    private ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe");
    }
}
