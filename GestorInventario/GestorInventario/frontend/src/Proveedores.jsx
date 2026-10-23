import { useErroresFormulario } from './useErroresFormulario'
import { IndicadorObligatorio, LeyendaObligatorios } from './CamposObligatorios'
import { fetchApi, leerRespuesta } from './api'
import { useEffect, useRef, useState } from 'react'

export default function Proveedores({ token, rol }) {
  const [proveedores, setProveedores] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState('')
  const [revision, setRevision] = useState(0)
  const [formulario, setFormulario] = useState(null)
  const [eliminar, setEliminar] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const { error, setError, formProps, avisoProps, campoProps, errorCampo } = useErroresFormulario('proveedor', ocupado)
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
        const respuesta = await fetchApi('/api/proveedores', {
          headers: { Authorization: `Bearer ${token}` }, signal: controlador.signal,
        })
        const datos = await leerRespuesta(respuesta)
        if (!controlador.signal.aborted) setProveedores(datos)
      } catch (error) {
        if (!controlador.signal.aborted) setErrorCarga(error.message)
      } finally {
        if (!controlador.signal.aborted) setCargando(false)
      }
    }
    cargar()
    return () => controlador.abort()
  }, [token, revision])

  function abrirFormulario(proveedor = { nombre: '', cif: '', telefono: '', email: '', direccion: '' }) {
    setError('')
    setExito('')
    setEliminar(null)
    setFormulario({ ...proveedor, cif: proveedor.cif ?? '', telefono: proveedor.telefono ?? '', email: proveedor.email ?? '', direccion: proveedor.direccion ?? '' })
  }

  async function guardar(evento) {
    evento.preventDefault()
    if (!esAdmin || enviando.current) return
    setError('')
    setExito('')
    if (!formulario.nombre.trim() || !formulario.cif.trim()) {
      setError('El nombre y el CIF son obligatorios y no pueden contener solo espacios.',
        ['nombre', 'cif'].filter((campo) => !formulario[campo].trim()).map((campo) => `proveedor-${campo}`))
      return
    }
    enviando.current = true
    setOcupado(true)
    const editando = formulario.id != null
    try {
      const respuesta = await fetchApi(editando ? `/api/proveedores/${formulario.id}` : '/api/proveedores', {
        method: editando ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ nombre: formulario.nombre.trim(), cif: formulario.cif.trim(), telefono: formulario.telefono.trim(), email: formulario.email.trim(), direccion: formulario.direccion }),
      })
      const proveedor = await leerRespuesta(respuesta)
      if (!activo.current) return
      setProveedores((anteriores) => editando
        ? anteriores.map((anterior) => anterior.id === proveedor.id ? proveedor : anterior)
        : [...anteriores, proveedor])
      setFormulario(null)
      setExito(editando ? 'Proveedor actualizado correctamente.' : 'Proveedor creado correctamente.')
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
      const respuesta = await fetchApi(`/api/proveedores/${eliminar.id}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
      })
      await leerRespuesta(respuesta)
      if (!activo.current) return
      setProveedores((anteriores) => anteriores.filter((proveedor) => proveedor.id !== eliminar.id))
      setEliminar(null)
      setExito('Proveedor eliminado correctamente.')
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
        <h2>Listado de proveedores</h2>
        {esAdmin && <button type="button" disabled={cargando || Boolean(errorCarga) || ocupado} onClick={() => abrirFormulario()}>Nuevo proveedor</button>}
      </div>
      {!esAdmin && <p>Consulta de proveedores. Solo los administradores pueden modificarlos.</p>}
      {exito && <p className="exito" role="status">{exito}</p>}
      {!formulario && error && <p {...avisoProps}>{error}</p>}
      {esAdmin && formulario && (
        <form {...formProps} className="formulario-producto" onSubmit={guardar}>
          {error && <p {...avisoProps}>{error}</p>}
          <fieldset disabled={ocupado}>
            <legend>{formulario.id != null ? 'Editar proveedor' : 'Nuevo proveedor'}</legend>
            <LeyendaObligatorios />
            <label htmlFor="proveedor-nombre">Nombre<IndicadorObligatorio /></label>
            <input maxLength={255} {...campoProps('proveedor-nombre')} id="proveedor-nombre" value={formulario.nombre} required onChange={(evento) => setFormulario({ ...formulario, nombre: evento.target.value })} />
            {errorCampo('proveedor-nombre')}
            <label htmlFor="proveedor-cif">CIF<IndicadorObligatorio /></label>
            <input maxLength={255} {...campoProps('proveedor-cif')} id="proveedor-cif" value={formulario.cif} required onChange={(evento) => setFormulario({ ...formulario, cif: evento.target.value })} />
            {errorCampo('proveedor-cif')}
            <label htmlFor="proveedor-telefono">Teléfono (opcional)</label>
            <input maxLength={255} {...campoProps('proveedor-telefono')} id="proveedor-telefono" type="tel" value={formulario.telefono} onChange={(evento) => setFormulario({ ...formulario, telefono: evento.target.value })} />
            {errorCampo('proveedor-telefono')}
            <label htmlFor="proveedor-email">Correo electrónico (opcional)</label>
            <input maxLength={255} {...campoProps('proveedor-email')} id="proveedor-email" type="email" value={formulario.email} onChange={(evento) => setFormulario({ ...formulario, email: evento.target.value })} />
            {errorCampo('proveedor-email')}
            <label htmlFor="proveedor-direccion">Dirección (opcional)</label>
            <textarea maxLength={255} {...campoProps('proveedor-direccion')} id="proveedor-direccion" value={formulario.direccion} onChange={(evento) => setFormulario({ ...formulario, direccion: evento.target.value })} />
            {errorCampo('proveedor-direccion')}
          </fieldset>
          <div className="botones-formulario">
            <button type="submit" disabled={ocupado}>{ocupado ? 'Guardando...' : 'Guardar proveedor'}</button>
            <button type="button" disabled={ocupado} onClick={() => { setFormulario(null); setError('') }}>Cancelar</button>
          </div>
        </form>
      )}
      {esAdmin && eliminar && (
        <div className="formulario-producto" role="region" aria-labelledby="confirmar-proveedor">
          <h3 id="confirmar-proveedor">Eliminar proveedor</h3>
          <p>¿Quieres eliminar el proveedor «{eliminar.nombre}» (CIF: {eliminar.cif})? Esta acción no se puede deshacer.</p>
          <p>Si está utilizado por productos, el backend impedirá su eliminación.</p>
          <div className="botones-formulario">
            <button type="button" disabled={ocupado} onClick={confirmarEliminacion}>{ocupado ? 'Eliminando...' : 'Confirmar eliminación'}</button>
            <button type="button" disabled={ocupado} onClick={() => { setEliminar(null); setError('') }}>Cancelar</button>
          </div>
        </div>
      )}
      {cargando && <p role="status">Cargando proveedores...</p>}
      {errorCarga && <div role="alert"><p className="error">{errorCarga}</p><button type="button" onClick={() => setRevision((anterior) => anterior + 1)}>Reintentar consulta</button></div>}
      {!cargando && !errorCarga && (
        <div className="tabla-contenedor" tabIndex="0" role="region" aria-label="Listado de proveedores">
          <table className="tabla-productos">
            <thead><tr><th>ID</th><th>Nombre</th><th>CIF</th><th>Teléfono</th><th>Correo electrónico</th><th>Dirección</th>{esAdmin && <th>Acciones</th>}</tr></thead>
            <tbody>
              {proveedores.map((proveedor) => (
                <tr key={proveedor.id}>
                  <td>{proveedor.id}</td><td>{proveedor.nombre}</td><td>{proveedor.cif}</td><td>{proveedor.telefono || '-'}</td><td>{proveedor.email || '-'}</td><td>{proveedor.direccion || '-'}</td>
                  {esAdmin && <td><div className="botones-formulario">
                    <button type="button" disabled={ocupado} onClick={() => abrirFormulario(proveedor)}>Editar<span className="solo-lectores"> {proveedor.nombre}</span></button>
                    <button type="button" disabled={ocupado} onClick={() => { setFormulario(null); setError(''); setExito(''); setEliminar(proveedor) }}>Eliminar<span className="solo-lectores"> {proveedor.nombre}</span></button>
                  </div></td>}
                </tr>
              ))}
              {proveedores.length === 0 && <tr><td colSpan={esAdmin ? 7 : 6}>No hay proveedores registrados.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
