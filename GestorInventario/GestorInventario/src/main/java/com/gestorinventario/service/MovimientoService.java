package com.gestorinventario.service;

import com.gestorinventario.model.Movimiento;
import com.gestorinventario.model.Producto;
import com.gestorinventario.model.TipoMovimiento;
import com.gestorinventario.repository.MovimientoRepository;
import com.gestorinventario.repository.ProductoRepository;
import com.gestorinventario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public MovimientoService(MovimientoRepository movimientoRepository,
                             ProductoRepository productoRepository, UsuarioRepository usuarioRepository) {

        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Page<Movimiento> obtenerMovimientos(Long productoId, TipoMovimiento tipo, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual a 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size debe estar entre 1 y 100");
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id")));
        if (productoId == null) {
            return tipo == null ? movimientoRepository.findAll(pageable)
                    : movimientoRepository.findByTipo(tipo, pageable);
        }
        if (!productoRepository.existsById(productoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El producto no existe");
        }
        return tipo == null ? movimientoRepository.findByProductoId(productoId, pageable)
                : movimientoRepository.findByProductoIdAndTipo(productoId, tipo, pageable);
    }

    @Transactional
    public Movimiento registrarMovimiento(Long productoId,
                                          TipoMovimiento tipo,
                                          Integer cantidad,
                                          String observaciones, String usernameActor) {

        if (productoId == null || tipo == null) {
            throw new IllegalArgumentException("Producto y tipo de movimiento son obligatorios");
        }

        // Buscar el producto
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "El producto no existe"));

        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se pueden registrar movimientos sobre un producto inactivo");
        }

        // Comprobar que la cantidad sea válida
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que 0");
        }

        var usuario = usuarioRepository.findByUsername(usernameActor)
                .filter(actor -> Boolean.TRUE.equals(actor.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "El usuario del movimiento no existe o esta inactivo"));

        // Modificar el stock
        if (tipo == TipoMovimiento.ENTRADA) {

            if (cantidad > Integer.MAX_VALUE - producto.getStock()) {
                throw new IllegalArgumentException("La entrada supera el stock maximo permitido");
            }

            producto.setStock(
                    producto.getStock() + cantidad
            );

        } else if (tipo == TipoMovimiento.SALIDA) {

            // Comprobar que tenemos suficiente stock
            if (producto.getStock() < cantidad) {
                throw new IllegalArgumentException(
                        "No hay suficiente stock");
            }

            producto.setStock(
                    producto.getStock() - cantidad
            );

        } else {
            throw new IllegalArgumentException(
                    "Tipo de movimiento no válido");
        }

        // Crear el movimiento
        Movimiento movimiento = new Movimiento(
                producto,
                tipo,
                cantidad,
                observaciones
        );

        movimiento.setUsuario(usuario);

        // Guardar el nuevo stock
        productoRepository.save(producto);

        // Guardar el movimiento
        return movimientoRepository.save(movimiento);
    }
}
