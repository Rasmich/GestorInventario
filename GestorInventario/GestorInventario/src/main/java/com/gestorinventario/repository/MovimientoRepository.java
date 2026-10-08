package com.gestorinventario.repository;

import com.gestorinventario.model.Movimiento;
import com.gestorinventario.model.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    boolean existsByProductoId(Long productoId);
    Page<Movimiento> findByProductoId(Long productoId, Pageable pageable);
    Page<Movimiento> findByTipo(TipoMovimiento tipo, Pageable pageable);
    Page<Movimiento> findByProductoIdAndTipo(Long productoId, TipoMovimiento tipo, Pageable pageable);

}
