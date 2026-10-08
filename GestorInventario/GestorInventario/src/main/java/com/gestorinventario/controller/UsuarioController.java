package com.gestorinventario.controller;

import com.gestorinventario.dto.UsuarioRequest;
import com.gestorinventario.dto.UsuarioResponse;
import com.gestorinventario.dto.UsuarioActualizacionRequest;
import com.gestorinventario.dto.UsuarioEstadoRequest;
import com.gestorinventario.dto.UsuarioPasswordRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import com.gestorinventario.model.Usuario;
import com.gestorinventario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crearUsuario(@Valid @RequestBody UsuarioRequest request) {
        Usuario usuario = usuarioService.crearUsuario(request);
        UsuarioResponse respuesta = respuesta(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioService.listarUsuarios().stream().map(UsuarioController::respuesta).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtenerUsuario(@PathVariable Long id) {
        return respuesta(usuarioService.obtenerUsuario(id));
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizarUsuario(@PathVariable Long id,
            @Valid @RequestBody UsuarioActualizacionRequest request, Authentication authentication) {
        return respuesta(usuarioService.actualizarUsuario(id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/estado")
    public UsuarioResponse cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody UsuarioEstadoRequest request, Authentication authentication) {
        return respuesta(usuarioService.cambiarEstado(id, request.activo(), authentication.getName()));
    }

    @PatchMapping("/{id}/password")
    public UsuarioResponse cambiarPassword(@PathVariable Long id,
            @Valid @RequestBody UsuarioPasswordRequest request, Authentication authentication) {
        return respuesta(usuarioService.cambiarPassword(id, request.getPassword(), authentication.getName()));
    }

    private static UsuarioResponse respuesta(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getUsername(),
                usuario.getRol(), usuario.getActivo());
    }
}
