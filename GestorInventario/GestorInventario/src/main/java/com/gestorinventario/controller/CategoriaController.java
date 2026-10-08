package com.gestorinventario.controller;

import com.gestorinventario.dto.CategoriaRequest;
import com.gestorinventario.dto.CategoriaResponse;
import com.gestorinventario.mapper.ResponseMapper;
import com.gestorinventario.service.CategoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {
    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<CategoriaResponse> obtenerCategorias() {
        return categoriaService.obtenerCategorias().stream().map(ResponseMapper::toCategoria).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponse> obtenerCategoriaPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ResponseMapper.toCategoria(categoriaService.obtenerCategoriaPorId(id)));
    }

    @PostMapping
    public CategoriaResponse crearCategoria(@Valid @RequestBody CategoriaRequest request) {
        return ResponseMapper.toCategoria(categoriaService.crearCategoria(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponse> actualizarCategoria(
            @PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return ResponseEntity.ok(ResponseMapper.toCategoria(categoriaService.actualizarCategoria(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Long id) {
        categoriaService.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }
}
