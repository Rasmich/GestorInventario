import { useErroresFormulario } from './useErroresFormulario'
import { IndicadorObligatorio, LeyendaObligatorios } from './CamposObligatorios'
import { fetchApi, leerRespuesta } from './api'
import { useEffect, useRef, useState } from 'react'

export default function Categorias({ token, rol }) {
  const [categorias, setCategorias] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState('')
  const [revision, setRevision] = useState(0)
  const [formulario, setFormulario] = useState(null)
  const [eliminar, setEliminar] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const { error, setError, formProps, avisoProps, campoProps, errorCampo } = useErroresFormulario('categoria', ocupado)
  const [exito, setExito] = useState('')
  const activo = useRef(false)
  const enviando = useRef(false)
  const esAdmin = rol === 'ADMIN'

  useEffect(() => {
    activo.current = true
    return () => { activo.current = false }
  }, [])

  useEffect(() => {
    const controlador = new AbortController()
    async function cargar() {
      setCargando(true)
      setErrorCarga('')
      try {
        const respuesta = await fetchApi('/api/categorias', {
          headers: { Authorization: `Bearer ${token}` }, signal: controlador.signal,
        })
        const datos = await leerRespuesta(respuesta)
        if (!controlador.signal.aborted) setCategorias(datos)
      } catch (error) {
        if (!controlador.signal.aborted) setErrorCarga(error.message)
      } finally {
        if (!controlador.signal.aborted) setCargando(false)
      }
    }
    cargar()
    return () => controlador.abort()
  }, [token, revision])

  function abrirFormulario(categoria = { nombre: '', descripcion: '' }) {
    setError('')
    setExito('')
    setEliminar(null)
    setFormulario({ ...categoria, descripcion: categoria.descripcion ?? '' })
  }

  async function guardar(evento) {
    evento.preventDefault()
    if (!esAdmin || enviando.current) return
    setError('')
    setExito('')
    if (!formulario.nombre.trim()) {
      setError('El nombre es obligatorio y no puede contener solo espacios.', ['categoria-nombre'])
      return
    }
    enviando.current = true
    setOcupado(true)
    const editando = formulario.id != null
    try {
      const respuesta = await fetchApi(editando ? `/api/categorias/${formulario.id}` : '/api/categorias', {
        method: editando ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ nombre: formulario.nombre.trim(), descripcion: formulario.descripcion }),
      })
      const categoria = await leerRespuesta(respuesta)
      if (!activo.current) return
      setCategorias((anteriores) => editando
        ? anteriores.map((anterior) => anterior.id === categoria.id ? categoria : anterior)
        : [...anteriores, categoria])
      setFormulario(null)
      setExito(editando ? 'Categoría actualizada correctamente.' : 'Categoría creada correctamente.')
    } catch (error) {
      if (activo.current) setError(error)
    } finally {
      enviando.current = false
      if (activo.current) setOcupado(false)
    }
  }

  async function confirmarEliminacion() {
    if (!esAdmin || !eliminar || enviando.current) return
    enviando.current = true
    setOcupado(true)
    setError('')
    setExito('')
    try {
      const respuesta = await fetchApi(`/api/categorias/${eliminar.id}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
      })
      await leerRespuesta(respuesta)
      if (!activo.current) return
      setCategorias((anteriores) => anteriores.filter((categoria) => categoria.id !== eliminar.id))
      setEliminar(null)
      setExito('Categoría eliminada correctamente.')
    } catch (error) {
      if (activo.current) setError(error)
    } finally {
      enviando.current = false
      if (activo.current) setOcupado(false)
    }
  }

  return (
    <section className="panel">
      <div className="cabecera-productos">
        <h2>Listado de categorías</h2>
        {esAdmin && <button type="button" disabled={cargando || Boolean(errorCarga) || ocupado} onClick={() => abrirFormulario()}>Nueva categoría</button>}
      </div>
      {!esAdmin && <p>Consulta de categorías. Solo los administradores pueden modificarlas.</p>}
      {exito && <p className="exito" role="status">{exito}</p>}
      {!formulario && error && <p {...avisoProps}>{error}</p>}
      {esAdmin && formulario && (
        <form {...formProps} className="formulario-producto" onSubmit={guardar}>
          {error && <p {...avisoProps}>{error}</p>}
          <fieldset disabled={ocupado}>
            <legend>{formulario.id != null ? 'Editar categoría' : 'Nueva categoría'}</legend>
            <LeyendaObligatorios />
            <label htmlFor="categoria-nombre">Nombre<IndicadorObligatorio /></label>
            <input maxLength={255} {...campoProps('categoria-nombre')} id="categoria-nombre" value={formulario.nombre} required onChange={(evento) => setFormulario({ ...formulario, nombre: evento.target.value })} />
            {errorCampo('categoria-nombre')}
            <label htmlFor="categoria-descripcion">Descripción (opcional)</label>
            <textarea maxLength={255} {...campoProps('categoria-descripcion')} id="categoria-descripcion" value={formulario.descripcion} onChange={(evento) => setFormulario({ ...formulario, descripcion: evento.target.value })} />
            {errorCampo('categoria-descripcion')}
          </fieldset>
          <div className="botones-formulario">
            <button type="submit" disabled={ocupado}>{ocupado ? 'Guardando...' : 'Guardar categoría'}</button>
            <button type="button" disabled={ocupado} onClick={() => { setFormulario(null); setError('') }}>Cancelar</button>
          </div>
        </form>
      )}
      {esAdmin && eliminar && (
        <div className="formulario-producto" role="region" aria-labelledby="confirmar-categoria">
          <h3 id="confirmar-categoria">Eliminar categoría</h3>
          <p>¿Quieres eliminar la categoría «{eliminar.nombre}»? Esta acción no se puede deshacer.</p>
          <p>Si está utilizada por productos, el backend impedirá su eliminación.</p>
          <div className="botones-formulario">
            <button type="button" disabled={ocupado} onClick={confirmarEliminacion}>{ocupado ? 'Eliminando...' : 'Confirmar eliminación'}</button>
            <button type="button" disabled={ocupado} onClick={() => { setEliminar(null); setError('') }}>Cancelar</button>
          </div>
        </div>
      )}
      {cargando && <p role="status">Cargando categorías...</p>}
      {errorCarga && <div role="alert"><p className="error">{errorCarga}</p><button type="button" onClick={() => setRevision((anterior) => anterior + 1)}>Reintentar consulta</button></div>}
      {!cargando && !errorCarga && (
        <div className="tabla-contenedor" tabIndex="0" role="region" aria-label="Listado de categorías">
          <table className="tabla-productos">
            <thead><tr><th>ID</th><th>Nombre</th><th>Descripción</th>{esAdmin && <th>Acciones</th>}</tr></thead>
            <tbody>
              {categorias.map((categoria) => (
                <tr key={categoria.id}>
                  <td>{categoria.id}</td><td>{categoria.nombre}</td><td>{categoria.descripcion || '-'}</td>
                  {esAdmin && <td><div className="botones-formulario">
                    <button type="button" disabled={ocupado} onClick={() => abrirFormulario(categoria)}>Editar<span className="solo-lectores"> {categoria.nombre}</span></button>
                    <button type="button" disabled={ocupado} onClick={() => { setFormulario(null); setError(''); setExito(''); setEliminar(categoria) }}>Eliminar<span className="solo-lectores"> {categoria.nombre}</span></button>
                  </div></td>}
                </tr>
              ))}
              {categorias.length === 0 && <tr><td colSpan={esAdmin ? 4 : 3}>No hay categorías registradas.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
