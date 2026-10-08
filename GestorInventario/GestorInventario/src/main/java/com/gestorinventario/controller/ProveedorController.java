package com.gestorinventario.controller;

import com.gestorinventario.dto.ProveedorRequest;
import com.gestorinventario.dto.ProveedorResponse;
import com.gestorinventario.mapper.ResponseMapper;
import com.gestorinventario.service.ProveedorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {
    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public List<ProveedorResponse> obtenerProveedores() {
        return proveedorService.obtenerProveedores().stream().map(ResponseMapper::toProveedor).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProveedorResponse> obtenerProveedorPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ResponseMapper.toProveedor(proveedorService.obtenerProveedorPorId(id)));
    }

    @PostMapping
    public ProveedorResponse crearProveedor(@Valid @RequestBody ProveedorRequest request) {
        return ResponseMapper.toProveedor(proveedorService.crearProveedor(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProveedorResponse> actualizarProveedor(
            @PathVariable Long id, @Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.ok(ResponseMapper.toProveedor(proveedorService.actualizarProveedor(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProveedor(@PathVariable Long id) {
        proveedorService.eliminarProveedor(id);
        return ResponseEntity.noContent().build();
    }
}
