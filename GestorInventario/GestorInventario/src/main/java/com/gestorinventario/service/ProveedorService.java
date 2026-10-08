package com.gestorinventario.service;

import com.gestorinventario.dto.ProveedorRequest;
import com.gestorinventario.model.Proveedor;
import com.gestorinventario.repository.ProveedorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProveedorService {
    private final ProveedorRepository proveedorRepository;
    private final EliminacionService eliminacionService;

    public ProveedorService(ProveedorRepository proveedorRepository, EliminacionService eliminacionService) {
        this.proveedorRepository = proveedorRepository;
        this.eliminacionService = eliminacionService;
    }

    public List<Proveedor> obtenerProveedores() {
        return proveedorRepository.findAll();
    }

    public Proveedor obtenerProveedorPorId(Long id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no existe"));
    }

    @Transactional
    public Proveedor crearProveedor(ProveedorRequest request) {
        Proveedor proveedor = new Proveedor();
        aplicarDatos(proveedor, request);
        return proveedorRepository.save(proveedor);
    }

    @Transactional
    public Proveedor actualizarProveedor(Long id, ProveedorRequest request) {
        Proveedor proveedor = obtenerProveedorPorId(id);
        aplicarDatos(proveedor, request);
        return proveedorRepository.save(proveedor);
    }

    @Transactional
    public void eliminarProveedor(Long id) {
        eliminacionService.eliminarProveedor(id);
    }

    private void aplicarDatos(Proveedor proveedor, ProveedorRequest request) {
        proveedor.setNombre(request.getNombre());
        proveedor.setCif(request.getCif());
        proveedor.setTelefono(request.getTelefono());
        proveedor.setEmail(request.getEmail());
        proveedor.setDireccion(request.getDireccion());
    }
}
