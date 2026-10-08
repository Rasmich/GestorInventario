package com.gestorinventario.service;

import com.gestorinventario.repository.CategoriaRepository;
import com.gestorinventario.repository.ProveedorRepository;
import com.gestorinventario.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EliminacionService {
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;

    public EliminacionService(CategoriaRepository categoriaRepository,
                              ProveedorRepository proveedorRepository,
                              ProductoRepository productoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional
    public void eliminarCategoria(Long id) {
        var categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La categoria no existe"));
        // Incluye productos inactivos: siguen conservando su relacion.
        if (productoRepository.existsByCategoriaId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoria esta siendo utilizada por productos");
        }
        categoriaRepository.delete(categoria);
        categoriaRepository.flush();
    }

    @Transactional
    public void eliminarProveedor(Long id) {
        var proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El proveedor no existe"));
        if (productoRepository.existsByProveedorId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El proveedor esta siendo utilizado por productos");
        }
        proveedorRepository.delete(proveedor);
        proveedorRepository.flush();
    }
}
