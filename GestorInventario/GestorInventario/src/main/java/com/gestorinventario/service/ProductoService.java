package com.gestorinventario.service;

import com.gestorinventario.dto.ProductoRequest;
import com.gestorinventario.model.Categoria;
import com.gestorinventario.model.Producto;
import com.gestorinventario.model.Proveedor;
import com.gestorinventario.repository.CategoriaRepository;
import com.gestorinventario.repository.MovimientoRepository;
import com.gestorinventario.repository.ProductoRepository;
import com.gestorinventario.repository.ProveedorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProductoService {
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimientoRepository movimientoRepository;
    private final ProveedorRepository proveedorRepository;

    public ProductoService(ProductoRepository productoRepository,
                           CategoriaRepository categoriaRepository,
                           ProveedorRepository proveedorRepository,
                           MovimientoRepository movimientoRepository) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
        this.categoriaRepository = categoriaRepository;
        this.proveedorRepository = proveedorRepository;
    }

    public List<Producto> obtenerProductos() {
        return productoRepository.findAll();
    }

    public Optional<Producto> obtenerProductoPorId(Long id) {
        return productoRepository.findById(id);
    }

    public List<Producto> obtenerProductosConStockBajo() {
        return productoRepository.findProductosConStockBajo();
    }

    @Transactional
    public Producto crearProducto(ProductoRequest request) {
        // La entidad inicializa el stock a cero; la peticion no admite stock.
        Producto producto = new Producto();
        aplicarDatos(producto, request);
        return productoRepository.save(producto);
    }

    @Transactional
    public Producto actualizarProducto(Long id, ProductoRequest request) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "El producto no existe"));
        // Se conserva el stock y la version de la entidad cargada.
        aplicarDatos(producto, request);
        // @Version comprueba al escribir que nadie haya modificado el producto.
        return productoRepository.saveAndFlush(producto);
    }

    @Transactional
    public boolean eliminarProducto(Long id) {
        Optional<Producto> producto = productoRepository.findById(id);
        if (producto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El producto no existe");
        }
        if (movimientoRepository.existsByProductoId(id)) {
            producto.get().setActivo(false);
            productoRepository.saveAndFlush(producto.get());
        } else {
            productoRepository.delete(producto.get());
            productoRepository.flush();
        }
        return true;
    }

    private void aplicarDatos(Producto producto, ProductoRequest request) {
        Categoria categoria = categoriaRepository.findById(request.getCategoriaId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "La categoria no existe"));
        Proveedor proveedor = proveedorRepository.findById(request.getProveedorId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "El proveedor no existe"));
        producto.setCodigo(request.getCodigo());
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecioCompra(request.getPrecioCompra());
        producto.setPrecioVenta(request.getPrecioVenta());
        producto.setStockMinimo(request.getStockMinimo());
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
    }
}
