import { useEffect, useState } from 'react'
import { fetchApi, leerRespuesta } from './api'
import Icono from './Icono'

const numero = new Intl.NumberFormat('es-ES')

function fechaMovimiento(fecha) {
  if (!fecha) return 'Sin fecha'
  const valor = new Date(fecha)
  return Number.isNaN(valor.getTime()) ? fecha : valor.toLocaleString('es-ES')
}

function ErrorConsulta({ mensaje, alReintentar }) {
  return <div className="inicio-error" role="alert">
    <p className="error">{mensaje}</p>
    <button type="button" onClick={alReintentar}>Reintentar</button>
  </div>
}

export default function Inicio({ token }) {
  const [revision, setRevision] = useState(0)
  const [productos, setProductos] = useState({ cargando: true, datos: null, error: '' })
  const [movimientos, setMovimientos] = useState({ cargando: true, datos: null, error: '' })

  useEffect(() => {
    const controlador = new AbortController()

    async function consultar(ruta, guardar, esPagina = false) {
      guardar({ cargando: true, datos: null, error: '' })
      try {
        const respuesta = await fetchApi(ruta, {
          headers: { Authorization: `Bearer ${token}` },
          signal: controlador.signal,
        })
        const cuerpo = await leerRespuesta(respuesta)
        const datos = esPagina ? cuerpo?.content : cuerpo
        if (!Array.isArray(datos)) throw new Error('El servidor ha devuelto un formato de datos inesperado.')
        if (!controlador.signal.aborted) guardar({ cargando: false, datos, error: '' })
      } catch (error) {
        if (!controlador.signal.aborted) guardar({ cargando: false, datos: null, error: error.message })
      }
    }

    consultar('/api/productos', setProductos)
    consultar('/api/movimientos?page=0&size=5', setMovimientos, true)
    return () => controlador.abort()
  }, [token, revision])

  function actualizar() {
    setProductos({ cargando: true, datos: null, error: '' })
    setMovimientos({ cargando: true, datos: null, error: '' })
    setRevision((anterior) => anterior + 1)
  }

  const activos = productos.datos?.filter((producto) => producto.activo === true)
  const alertas = activos?.filter((producto) => producto.stock <= producto.stockMinimo)
  const tarjetas = [
    { titulo: 'Total de productos', valor: productos.datos?.length, detalle: 'Activos e inactivos', icono: 'productos', acento: 'azul' },
    { titulo: 'Productos activos', valor: activos?.length, detalle: 'Disponibles para operar', icono: 'actividad', acento: 'verde' },
    { titulo: 'Unidades disponibles', valor: activos?.reduce((total, producto) => total + producto.stock, 0), detalle: 'Stock de productos activos', icono: 'stock', acento: 'violeta' },
    { titulo: 'Productos que necesitan reposición', valor: alertas?.length, detalle: 'Stock igual o inferior al mínimo', icono: 'alerta', acento: 'ambar' },
  ]
  const cargando = productos.cargando || movimientos.cargando

  return <div className="inicio">
    <div className="inicio-cabecera">
      <div><h2>Resumen del inventario</h2><p>Existencias, reposición y actividad reciente.</p></div>
      <button type="button" onClick={actualizar} disabled={cargando}><Icono nombre="actualizar" />{cargando ? 'Actualizando…' : 'Actualizar'}</button>
    </div>

    <section aria-label="Indicadores del inventario" aria-busy={productos.cargando}>
      {productos.cargando && <p role="status">Cargando indicadores y alertas…</p>}
      {productos.error && <ErrorConsulta mensaje={productos.error} alReintentar={actualizar} />}
      <dl className="inicio-tarjetas">
        {tarjetas.map(({ titulo, valor, detalle, icono, acento }) => <div className="panel inicio-tarjeta" data-acento={acento} key={titulo}>
          <dt><span>{titulo}</span><span className="tarjeta-icono"><Icono nombre={icono} /></span></dt>
          <dd>{productos.cargando ? '…' : productos.error ? 'No disponible' : numero.format(valor)}</dd>
          <p>{detalle}</p>
        </div>)}
      </dl>
    </section>

    <section className="panel" aria-labelledby="inicio-alertas" aria-busy={productos.cargando}>
      <h2 id="inicio-alertas"><span className="seccion-icono seccion-icono-ambar"><Icono nombre="alerta" /></span>Alertas de reposición</h2>
      <p>Productos activos en el mínimo o por debajo, incluidos los que están sin stock.</p>
      {productos.cargando ? <p role="status">Cargando alertas…</p>
        : productos.error ? <p>No se pueden mostrar las alertas. Reintenta la consulta de productos.</p>
        : productos.datos.length === 0 ? <p>No hay productos registrados.</p>
        : alertas.length === 0 ? <p>No hay productos activos que necesiten reposición.</p>
        : <div className="tabla-contenedor" tabIndex="0" role="region" aria-label="Productos que necesitan reposición">
          <table className="tabla-productos">
            <thead><tr><th scope="col">Código</th><th scope="col">Nombre</th><th scope="col">Stock actual</th><th scope="col">Mínimo</th></tr></thead>
            <tbody>{alertas.map((producto) => <tr key={producto.id}>
              <td>{producto.codigo}</td><td>{producto.nombre}</td>
              <td><span className={`inicio-stock ${producto.stock === 0 ? 'inicio-sin-stock' : ''}`}>{numero.format(producto.stock)}{producto.stock === 0 && ' · Sin stock'}</span></td>
              <td>{numero.format(producto.stockMinimo)}</td>
            </tr>)}</tbody>
          </table>
        </div>}
    </section>

    <section className="panel" aria-labelledby="inicio-movimientos" aria-busy={movimientos.cargando}>
      <h2 id="inicio-movimientos"><span className="seccion-icono"><Icono nombre="movimientos" /></span>Últimos movimientos</h2>
      <p>Las 5 operaciones más recientes del inventario.</p>
      {movimientos.cargando ? <p role="status">Cargando movimientos…</p>
        : movimientos.error ? <ErrorConsulta mensaje={movimientos.error} alReintentar={actualizar} />
        : movimientos.datos.length === 0 ? <p>No hay movimientos registrados.</p>
        : <div className="tabla-contenedor" tabIndex="0" role="region" aria-label="Últimos cinco movimientos">
          <table className="tabla-productos">
            <thead><tr><th scope="col">Producto</th><th scope="col">Tipo</th><th scope="col">Cantidad</th><th scope="col">Fecha</th></tr></thead>
            <tbody>{movimientos.datos.map((movimiento) => <tr key={movimiento.id}>
              <td>{movimiento.producto?.nombre ?? 'Producto no disponible'}<small className="inicio-codigo">{movimiento.producto?.codigo}</small></td>
              <td><span className={`inicio-tipo ${movimiento.tipo === 'ENTRADA' ? 'inicio-entrada' : 'inicio-salida'}`}>{movimiento.tipo === 'ENTRADA' ? 'Entrada' : movimiento.tipo === 'SALIDA' ? 'Salida' : movimiento.tipo}</span></td>
              <td>{numero.format(movimiento.cantidad)}</td><td>{fechaMovimiento(movimiento.fecha)}</td>
            </tr>)}</tbody>
          </table>
        </div>}
    </section>
  </div>
}
