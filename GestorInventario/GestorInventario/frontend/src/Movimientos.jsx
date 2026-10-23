import { useErroresFormulario } from './useErroresFormulario'
import { IndicadorObligatorio, LeyendaObligatorios } from './CamposObligatorios'
import { fetchApi, leerRespuesta } from './api'
import { useEffect, useRef, useState } from 'react'

function formatearFecha(fecha) {
  if (!fecha) return '-'
  const valor = new Date(fecha)
  return Number.isNaN(valor.getTime()) ? fecha : valor.toLocaleString('es-ES')
}

export default function Movimientos({ token, productos, cargandoProductos, errorProductos, alGuardar }) {
  const [historial, setHistorial] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [filtroProductoId, setFiltroProductoId] = useState('')
  const [filtroTipo, setFiltroTipo] = useState('')
  const [revision, setRevision] = useState(0)
  const [cargando, setCargando] = useState(true)
  const [errorHistorial, setErrorHistorial] = useState('')
  const [productoId, setProductoId] = useState('')
  const [tipo, setTipo] = useState('ENTRADA')
  const [cantidad, setCantidad] = useState('')
  const [guardando, setGuardando] = useState(false)
  const { error, setError, formProps, avisoProps, campoProps, errorCampo } = useErroresFormulario('movimiento', guardando)
  const [exito, setExito] = useState('')
  const activo = useRef(false)
  const envioEnCurso = useRef(false)

  useEffect(() => {
    activo.current = true
    return () => { activo.current = false }
  }, [])

  useEffect(() => {
    const controlador = new AbortController()
    async function cargarHistorial() {
      setCargando(true)
      setErrorHistorial('')
      setHistorial(null)
      let ajustandoPagina = false
      try {
        const parametros = new URLSearchParams({ page: String(pagina), size: '20' })
        if (filtroProductoId) parametros.set('productoId', filtroProductoId)
        if (filtroTipo) parametros.set('tipo', filtroTipo)
        const respuesta = await fetchApi(`/api/movimientos?${parametros}`, {
          headers: { Authorization: `Bearer ${token}` },
          signal: controlador.signal,
        })
        const datos = await leerRespuesta(respuesta)
        if (controlador.signal.aborted) return
        if (!Array.isArray(datos?.content) || datos.page !== pagina
          || !Number.isInteger(datos.totalPages) || datos.totalPages < 0
          || !Number.isSafeInteger(datos.totalElements) || datos.totalElements < 0) {
          throw new Error('El servidor ha devuelto una página de movimientos no válida.')
        }
        const ultimaPagina = Math.max(0, datos.totalPages - 1)
        if (pagina > ultimaPagina) {
          ajustandoPagina = true
          setPagina(ultimaPagina)
          return
        }
        setHistorial(datos)
      } catch (error) {
        if (!controlador.signal.aborted) setErrorHistorial(error.message)
      } finally {
        if (!controlador.signal.aborted && !ajustandoPagina) setCargando(false)
      }
    }
    cargarHistorial()
    return () => controlador.abort()
  }, [token, pagina, revision, filtroProductoId, filtroTipo])

  function prepararConsulta() {
    setHistorial(null)
    setErrorHistorial('')
    setCargando(true)
  }

  function cambiarFiltro(guardar, valor) {
    prepararConsulta()
    guardar(valor)
    setPagina(0)
  }

  function limpiarFiltros() {
    prepararConsulta()
    setFiltroProductoId('')
    setFiltroTipo('')
    setPagina(0)
  }

  function cambiarPagina(nuevaPagina) {
    prepararConsulta()
    setPagina(nuevaPagina)
  }

  async function registrar(evento) {
    evento.preventDefault()
    if (envioEnCurso.current) return
    setError('')
    setExito('')
    const unidades = Number(cantidad)
    if (!productoId || !Number.isInteger(unidades) || unidades <= 0 || unidades > 2147483647) {
      setError('Selecciona un producto e indica una cantidad entera positiva válida.', [
        ...(!productoId ? ['movimiento-producto'] : []),
        ...(!Number.isInteger(unidades) || unidades <= 0 || unidades > 2147483647 ? ['movimiento-cantidad'] : []),
      ])
      return
    }
    envioEnCurso.current = true
    setGuardando(true)
    try {
      const respuesta = await fetchApi('/api/movimientos', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ productoId: Number(productoId), tipo, cantidad: unidades }),
      })
      const movimiento = await leerRespuesta(respuesta)
      if (!activo.current) return
      alGuardar(movimiento)
      setCantidad('')
      setExito('Movimiento registrado correctamente.')
      prepararConsulta()
      setPagina(0)
      setRevision((anterior) => anterior + 1)
    } catch (error) {
      if (activo.current) setError(error)
    } finally {
      envioEnCurso.current = false
      if (activo.current) setGuardando(false)
    }
  }

  const productosActivos = productos.filter((producto) => producto.activo)
  const seleccionado = productosActivos.find((producto) => String(producto.id) === productoId)

  return (
    <section className="panel">
      <h2>Movimientos de inventario</h2>
      <form {...formProps} className="formulario-producto" onSubmit={registrar}>
        <fieldset disabled={guardando || cargandoProductos || Boolean(errorProductos)}>
          <legend>Registrar movimiento</legend>
          <LeyendaObligatorios />
          <label htmlFor="movimiento-producto">Producto<IndicadorObligatorio /></label>
          <select {...campoProps('movimiento-producto')} id="movimiento-producto" value={productoId} onChange={(evento) => setProductoId(evento.target.value)} required>
            <option value="">Selecciona un producto</option>
            {productosActivos.map((producto) => (
              <option key={producto.id} value={producto.id}>{producto.codigo} — {producto.nombre}</option>
            ))}
          </select>
            {errorCampo('movimiento-producto')}
          {seleccionado && <p>Stock disponible: {seleccionado.stock}</p>}
          <label htmlFor="movimiento-tipo">Tipo<IndicadorObligatorio /></label>
          <select {...campoProps('movimiento-tipo')} id="movimiento-tipo" value={tipo} onChange={(evento) => setTipo(evento.target.value)}>
            <option value="ENTRADA">Entrada</option>
            <option value="SALIDA">Salida</option>
          </select>
            {errorCampo('movimiento-tipo')}
          <label htmlFor="movimiento-cantidad">Cantidad<IndicadorObligatorio /></label>
          <input {...campoProps('movimiento-cantidad')} id="movimiento-cantidad" type="number" min="1" max="2147483647" step="1" value={cantidad} onChange={(evento) => setCantidad(evento.target.value)} required />
            {errorCampo('movimiento-cantidad')}
        </fieldset>
        {cargandoProductos && <p role="status">Cargando productos...</p>}
        {errorProductos && <p className="error" role="alert">{errorProductos}</p>}
        {!cargandoProductos && !errorProductos && productosActivos.length === 0 && <p>No hay productos activos para registrar movimientos.</p>}
        {error && <p {...avisoProps}>{error}</p>}
        {exito && <p className="exito" role="status">{exito}</p>}
        <button type="submit" disabled={guardando || cargandoProductos || Boolean(errorProductos) || !seleccionado}>
          {guardando ? 'Registrando...' : 'Registrar movimiento'}
        </button>
      </form>

      <h3>Historial de movimientos</h3>
      <div className="filtros-productos filtros-movimientos" role="group" aria-label="Filtros del historial">
        <label htmlFor="historial-producto">Producto
          <select id="historial-producto" value={filtroProductoId}
            disabled={cargandoProductos || Boolean(errorProductos)}
            aria-describedby={cargandoProductos || errorProductos ? 'historial-productos-estado' : undefined}
            onChange={(evento) => cambiarFiltro(setFiltroProductoId, evento.target.value)}>
            <option value="">Todos los productos</option>
            {productos.map((producto) => <option key={producto.id} value={producto.id}>
              {producto.codigo} — {producto.nombre}{!producto.activo ? ' (inactivo)' : ''}
            </option>)}
          </select>
        </label>
        <label htmlFor="historial-tipo">Tipo
          <select id="historial-tipo" value={filtroTipo}
            onChange={(evento) => cambiarFiltro(setFiltroTipo, evento.target.value)}>
            <option value="">Todos los tipos</option>
            <option value="ENTRADA">Entrada</option>
            <option value="SALIDA">Salida</option>
          </select>
        </label>
        <button type="button" onClick={limpiarFiltros} disabled={!filtroProductoId && !filtroTipo}>Limpiar filtros</button>
      </div>
      {(cargandoProductos || errorProductos) && <p id="historial-productos-estado" role="status">
        {cargandoProductos ? 'Cargando opciones de producto…' : 'No se han podido cargar las opciones de producto. Puedes consultar el historial y filtrar por tipo. Usa Limpiar filtros para quitar un producto seleccionado.'}
      </p>}
      {cargando && <p role="status">Cargando movimientos...</p>}
      {errorHistorial && <div role="alert"><p className="error">{errorHistorial}</p><button type="button" onClick={() => { prepararConsulta(); setRevision((anterior) => anterior + 1) }}>Reintentar consulta</button></div>}
      {!cargando && !errorHistorial && historial && (
        <>
          <div className="tabla-contenedor" tabIndex="0" role="region" aria-label="Historial de movimientos">
            <table className="tabla-productos">
              <thead><tr><th>ID</th><th>Fecha</th><th>Código</th><th>Producto</th><th>Tipo</th><th>Cantidad</th><th>Usuario</th><th>Observaciones</th></tr></thead>
              <tbody>
                {historial.content.map((movimiento) => (
                  <tr key={movimiento.id}>
                    <td>{movimiento.id}</td><td>{formatearFecha(movimiento.fecha)}</td>
                    <td>{movimiento.producto?.codigo ?? '-'}</td><td>{movimiento.producto?.nombre ?? '-'}</td>
                    <td>{movimiento.tipo}</td><td>{movimiento.cantidad}</td>
                    <td>{movimiento.usuario?.username ?? '-'}</td><td>{movimiento.observaciones || '-'}</td>
                  </tr>
                ))}
                {historial.content.length === 0 && <tr><td colSpan="8">{filtroProductoId || filtroTipo
                  ? 'No hay movimientos que coincidan con los filtros seleccionados.'
                  : 'No hay movimientos registrados.'}</td></tr>}
              </tbody>
            </table>
          </div>
          <div className="paginacion" aria-label="Paginación del historial">
            <button type="button" disabled={pagina === 0 || historial.totalElements === 0 || guardando} onClick={() => cambiarPagina(pagina - 1)}>Anterior</button>
            <p role="status">{historial.totalElements === 0 ? '0 movimientos'
              : `Página ${historial.page + 1} de ${historial.totalPages} · ${historial.totalElements} movimientos`}</p>
            <button type="button" disabled={pagina + 1 >= historial.totalPages || historial.totalElements === 0 || guardando} onClick={() => cambiarPagina(pagina + 1)}>Siguiente</button>
          </div>
        </>
      )}
    </section>
  )
}
