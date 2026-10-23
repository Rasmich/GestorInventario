import { useEffect, useRef, useState } from 'react'
import { fetchApi, leerRespuesta } from './api'
import FormularioProducto from './FormularioProducto'

const estadosStock = { STOCK_CORRECTO: 'Stock correcto', STOCK_BAJO: 'Stock bajo', SIN_STOCK: 'Sin stock' }
const euros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })

export default function Productos({ token, rol, productos, cargando, errorCarga, alGuardar, alRecargar }) {
  const [formulario, setFormulario] = useState(null)
  const [eliminar, setEliminar] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [estado, setEstado] = useState('todos')
  const [stock, setStock] = useState('todos')
  const activo = useRef(false)
  const enviando = useRef(false)
  const esAdmin = rol === 'ADMIN'

  useEffect(() => {
    activo.current = true
    return () => { activo.current = false }
  }, [])

  function abrir(producto = {}) {
    setFormulario(producto)
    setEliminar(null)
    setError('')
    setExito('')
  }

  async function confirmarEliminacion() {
    if (!esAdmin || !eliminar || enviando.current) return
    enviando.current = true
    setOcupado(true)
    setError('')
    setExito('')
    try {
      const respuesta = await fetchApi(`/api/productos/${eliminar.id}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
      })
      await leerRespuesta(respuesta)
      if (!activo.current) return
      setEliminar(null)
      setExito('Operación completada. El producto se ha eliminado si no tenía movimientos o se ha desactivado conservando su historial y stock.')
      alRecargar()
    } catch (error) {
      if (activo.current) setError(error.message)
    } finally {
      enviando.current = false
      if (activo.current) setOcupado(false)
    }
  }

  const texto = busqueda.trim().toLocaleLowerCase('es')
  const visibles = productos.filter((producto) =>
    (estado === 'todos' || producto.activo === (estado === 'activos'))
    && (stock === 'todos' || producto.estadoStock === stock)
    && [producto.codigo, producto.nombre, producto.categoria?.nombre, producto.proveedor?.nombre]
      .some((valor) => valor?.toLocaleLowerCase('es').includes(texto)))

  return (
    <section className="panel">
      <div className="cabecera-productos">
        <h2>Listado de productos</h2>
        {esAdmin && <button type="button" disabled={cargando || Boolean(errorCarga) || ocupado || Boolean(formulario)} onClick={() => abrir()}>Nuevo producto</button>}
      </div>
      <p>El stock se modifica exclusivamente desde Movimientos. Los productos inactivos no admiten entradas ni salidas.</p>
      {!esAdmin && <p>Consulta de productos. Solo los administradores pueden modificarlos.</p>}
      {exito && <p className="exito" role="status">{exito}</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {esAdmin && formulario && <FormularioProducto
        key={formulario.id ?? 'nuevo'} token={token} productoInicial={formulario.id != null ? formulario : null}
        alGuardar={(producto) => {
          alGuardar(producto)
          setFormulario(null)
          setExito(formulario.id != null ? 'Producto actualizado correctamente.' : 'Producto creado correctamente.')
        }}
        alCancelar={() => setFormulario(null)}
      />}
      {esAdmin && eliminar && <div className="formulario-producto" role="region" aria-labelledby="confirmar-producto">
        <h3 id="confirmar-producto">Eliminar o desactivar producto</h3>
        <p>¿Quieres dar de baja «{eliminar.nombre}»?</p>
        <p>Sin movimientos se eliminará definitivamente. Con movimientos se desactivará y conservará su historial y su stock ({eliminar.stock} unidades).</p>
        <p>Si conserva existencias, no podrás registrar salidas una vez desactivado. Actualmente no hay una opción de reactivación.</p>
        <div className="botones-formulario">
          <button type="button" disabled={ocupado} onClick={confirmarEliminacion}>{ocupado ? 'Procesando...' : 'Confirmar baja'}</button>
          <button type="button" disabled={ocupado} onClick={() => { setEliminar(null); setError('') }}>Cancelar</button>
        </div>
      </div>}
      <div className="filtros-productos">
        <label>Buscar por código, nombre, categoría o proveedor
          <input type="search" value={busqueda} onChange={(evento) => setBusqueda(evento.target.value)} />
        </label>
        <label>Estado del producto
          <select value={estado} onChange={(evento) => setEstado(evento.target.value)}>
            <option value="todos">Todos</option><option value="activos">Activos</option><option value="inactivos">Inactivos</option>
          </select>
        </label>
        <label>Estado del stock
          <select value={stock} onChange={(evento) => setStock(evento.target.value)}>
            <option value="todos">Todos</option>
            {Object.entries(estadosStock).map(([valor, etiqueta]) => <option key={valor} value={valor}>{etiqueta}</option>)}
          </select>
        </label>
        <button type="button" onClick={() => { setBusqueda(''); setEstado('todos'); setStock('todos') }}>Limpiar filtros</button>
        <button type="button" disabled={cargando || ocupado || Boolean(formulario)} onClick={alRecargar}>Actualizar listado</button>
      </div>
      {cargando && <p role="status">Cargando productos...</p>}
      {errorCarga && <div role="alert"><p className="error">{errorCarga}</p><button type="button" disabled={cargando || ocupado} onClick={alRecargar}>Reintentar consulta</button></div>}
      {!cargando && !errorCarga && <>
        <p role="status">{visibles.length} de {productos.length} productos</p>
        <div className="tabla-contenedor" tabIndex="0" role="region" aria-label="Listado de productos">
          <table className="tabla-productos">
            <thead><tr><th>Código</th><th>Nombre</th><th>Categoría</th><th>Proveedor</th><th>Precio venta</th><th>Stock</th><th>Stock mínimo</th><th>Estado del stock</th><th>Estado del producto</th>{esAdmin && <th>Acciones</th>}</tr></thead>
            <tbody>
              {visibles.map((producto) => <tr key={producto.id}>
                <td>{producto.codigo}</td><td>{producto.nombre}</td><td>{producto.categoria?.nombre ?? '-'}</td><td>{producto.proveedor?.nombre ?? '-'}</td>
                <td>{producto.precioVenta == null ? '-' : euros.format(producto.precioVenta)}</td><td>{producto.stock}</td><td>{producto.stockMinimo}</td>
                <td>{estadosStock[producto.estadoStock] ?? '-'}</td><td><span className={`estado-producto ${producto.activo ? 'estado-activo' : 'estado-inactivo'}`}>{producto.activo ? 'Activo' : 'Inactivo'}</span></td>
                {esAdmin && <td><div className="botones-formulario">
                  <button type="button" disabled={ocupado || Boolean(formulario) || Boolean(eliminar)} onClick={() => abrir(producto)}>Editar<span className="solo-lectores"> {producto.nombre}</span></button>
                  {producto.activo && <button type="button" disabled={ocupado || Boolean(formulario) || Boolean(eliminar)} onClick={() => { setEliminar(producto); setError(''); setExito('') }}>Eliminar / desactivar<span className="solo-lectores"> {producto.nombre}</span></button>}
                </div></td>}
              </tr>)}
              {visibles.length === 0 && <tr><td colSpan={esAdmin ? 10 : 9}>{productos.length === 0 ? 'No hay productos registrados.' : 'No hay productos que coincidan con los filtros.'}</td></tr>}
            </tbody>
          </table>
        </div>
      </>}
    </section>
  )
}
