package com.gestorinventario.controller;

import com.gestorinventario.dto.MovimientoRequest;
import com.gestorinventario.dto.MovimientoResponse;
import com.gestorinventario.dto.PaginaResponse;
import com.gestorinventario.mapper.ResponseMapper;
import com.gestorinventario.model.TipoMovimiento;
import com.gestorinventario.service.MovimientoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private final MovimientoService movimientoService;

    public MovimientoController(MovimientoService movimientoService) {

        this.movimientoService = movimientoService;
    }

    // Obtener todos los movimientos o el historial de un producto
    @GetMapping
    public PaginaResponse<MovimientoResponse> obtenerMovimientos(@RequestParam(required = false) Long productoId,
                                                      @RequestParam(required = false) TipoMovimiento tipo,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        var movimientos = movimientoService.obtenerMovimientos(productoId, tipo, page, size);
        return new PaginaResponse<>(movimientos.getContent().stream().map(ResponseMapper::toMovimiento).toList(),
                movimientos.getNumber(), movimientos.getSize(),
                movimientos.getTotalElements(), movimientos.getTotalPages());
    }

    // Registrar una entrada o salida
    @PostMapping
    public ResponseEntity<MovimientoResponse> registrarMovimiento(
            @Valid @RequestBody MovimientoRequest request, Authentication authentication) {

        var movimiento = movimientoService.registrarMovimiento(
                request.getProductoId(),
                request.getTipo(),
                request.getCantidad(),
                request.getObservaciones(), authentication.getName());
        return ResponseEntity.ok(ResponseMapper.toMovimiento(movimiento));
    }
}
