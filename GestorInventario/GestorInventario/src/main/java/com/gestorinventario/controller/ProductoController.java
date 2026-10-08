package com.gestorinventario.controller;

import com.gestorinventario.dto.ProductoRequest;
import com.gestorinventario.dto.ProductoResponse;
import com.gestorinventario.mapper.ResponseMapper;
import com.gestorinventario.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {
    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<ProductoResponse> obtenerProductos() {
        return productoService.obtenerProductos().stream().map(ResponseMapper::toProducto).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtenerProductoPorId(@PathVariable Long id) {
        return productoService.obtenerProductoPorId(id)
                .map(ResponseMapper::toProducto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El producto no existe"));
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crearProducto(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(ResponseMapper.toProducto(productoService.crearProducto(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizarProducto(
            @PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(ResponseMapper.toProducto(productoService.actualizarProducto(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable Long id) {
        if (!productoService.eliminarProducto(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El producto no existe");
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stock-bajo")
    public List<ProductoResponse> obtenerProductosConStockBajo() {
        return productoService.obtenerProductosConStockBajo().stream().map(ResponseMapper::toProducto).toList();
    }
}
