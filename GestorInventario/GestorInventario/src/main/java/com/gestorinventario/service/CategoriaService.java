package com.gestorinventario.service;

import com.gestorinventario.dto.CategoriaRequest;
import com.gestorinventario.model.Categoria;
import com.gestorinventario.repository.CategoriaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import com.gestorinventario.error.CampoUnico;
import com.gestorinventario.error.IntegridadDatos;

@Service
@Transactional(readOnly = true)
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;
    private final EliminacionService eliminacionService;

    public CategoriaService(CategoriaRepository categoriaRepository, EliminacionService eliminacionService) {
        this.categoriaRepository = categoriaRepository;
        this.eliminacionService = eliminacionService;
    }

    public List<Categoria> obtenerCategorias() {
        return categoriaRepository.findAll();
    }

    public Categoria obtenerCategoriaPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no existe"));
    }

    @Transactional
    public Categoria crearCategoria(CategoriaRequest request) {
        Categoria categoria = new Categoria();
        aplicarDatos(categoria, request);
        return guardarCategoria(categoria);
    }

    @Transactional
    public Categoria actualizarCategoria(Long id, CategoriaRequest request) {
        Categoria categoria = obtenerCategoriaPorId(id);
        aplicarDatos(categoria, request);
        return guardarCategoria(categoria);
    }

    @Transactional
    public void eliminarCategoria(Long id) {
        eliminacionService.eliminarCategoria(id);
    }

    private Categoria guardarCategoria(Categoria categoria) {
        return IntegridadDatos.guardar(CampoUnico.CATEGORIA, () -> {
            Categoria guardada = categoriaRepository.save(categoria);
            // En edición el UPDATE puede aplazarse hasta el commit. Ejecutarlo aquí
            // conserva el contexto de categoría al traducir el conflicto.
            categoriaRepository.flush();
            return guardada;
        });
    }

    private void aplicarDatos(Categoria categoria, CategoriaRequest request) {
        categoria.setNombre(request.getNombre());
        categoria.setDescripcion(request.getDescripcion());
    }
}
