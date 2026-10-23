import { useErroresFormulario } from './useErroresFormulario'
import { IndicadorObligatorio, LeyendaObligatorios } from './CamposObligatorios'
import { fetchApi, leerRespuesta } from './api'
import { useEffect, useRef, useState } from 'react'

export default function Usuarios({ usuarioActual, alActualizarPropio, alCerrarSesion }) {
  const [usuarios, setUsuarios] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState('')
  const [revision, setRevision] = useState(0)
  const [formulario, setFormulario] = useState(null)
  const [estado, setEstado] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const { error, setError, formProps, avisoProps, campoProps, errorCampo } = useErroresFormulario('usuario', ocupado)
  const [exito, setExito] = useState('')
  const activo = useRef(false)
  const enviando = useRef(false)
  const { token, rol } = usuarioActual

  useEffect(() => {
    activo.current = true
    return () => { activo.current = false }
  }, [])

  useEffect(() => {
    if (rol !== 'ADMIN') return
    const controlador = new AbortController()
    async function cargar() {
      setCargando(true)
      setErrorCarga('')
      try {
        const respuesta = await fetchApi('/api/usuarios', {
          headers: { Authorization: `Bearer ${token}` }, signal: controlador.signal,
        })
        const datos = await leerRespuesta(respuesta)
        if (!controlador.signal.aborted) setUsuarios(datos)
      } catch (error) {
        if (!controlador.signal.aborted) setErrorCarga(error.message)
      } finally {
        if (!controlador.signal.aborted) setCargando(false)
      }
    }
    cargar()
    return () => controlador.abort()
  }, [token, rol, revision])

  const administradores = usuarios.filter((usuario) => usuario.activo && usuario.rol === 'ADMIN').length
  const esUltimo = (usuario) => usuario.activo && usuario.rol === 'ADMIN' && administradores <= 1

  function abrir(modo, usuario = { nombre: '', username: '', rol: 'EMPLEADO' }) {
    setError('')
    setExito('')
    setEstado(null)
    setFormulario({ ...usuario, modo, password: '' })
  }

  async function enviar(ruta, metodo, datos, mensaje) {
    if (rol !== 'ADMIN' || enviando.current) return
    enviando.current = true
    setOcupado(true)
    setError('')
    setExito('')
    try {
      const respuesta = await fetchApi(ruta, {
        method: metodo,
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(datos),
      })
      const usuario = await leerRespuesta(respuesta)
      if (!activo.current) return
      setFormulario(null)
      setEstado(null)
      setUsuarios((anteriores) => anteriores.some((anterior) => anterior.id === usuario.id)
        ? anteriores.map((anterior) => anterior.id === usuario.id ? usuario : anterior)
        : [...anteriores, usuario].sort((a, b) => a.id - b.id))
      if (usuario.id === usuarioActual.id) {
        if (usuario.username !== usuarioActual.username || usuario.rol !== usuarioActual.rol || !usuario.activo) {
          alCerrarSesion('Tus datos de acceso han cambiado. Inicia sesión de nuevo.')
          return
        }
        alActualizarPropio(usuario)
      }
      setExito(mensaje)
    } catch (error) {
      if (activo.current) setError(error)
    } finally {
      enviando.current = false
      if (activo.current) setOcupado(false)
    }
  }

  function guardar(evento) {
    evento.preventDefault()
    setError('')
    const { modo, id, nombre, username, password, rol } = formulario
    if (modo !== 'password') {
      if (!nombre.trim() || nombre.length > 255 || !username || username.length > 255 || /\s/u.test(username)) {
        setError('Indica un nombre no vacío y un username sin espacios, ambos de hasta 255 caracteres.', [
          ...(!nombre.trim() || nombre.length > 255 ? ['usuario-nombre'] : []),
          ...(!username || username.length > 255 || /\s/u.test(username) ? ['usuario-username'] : []),
        ])
        return
      }
      if (!['ADMIN', 'EMPLEADO'].includes(rol)) {
        setError('Selecciona un rol válido.', ['usuario-rol'])
        return
      }
      if (modo === 'editar' && esUltimo(usuarios.find((usuario) => usuario.id === id) ?? {}) && rol !== 'ADMIN') {
        setError('Debe existir al menos un administrador activo.', ['usuario-rol'])
        return
      }
    }
    if (modo !== 'editar' && (!password.trim() || password.length < 8 || password.length > 72 || new TextEncoder().encode(password).length > 72)) {
      setError('La contraseña requiere al menos 8 caracteres y un máximo de 72 bytes UTF-8.', ['usuario-password'])
      return
    }
    if (modo === 'password') {
      enviar(`/api/usuarios/${id}/password`, 'PATCH', { password }, 'Contraseña actualizada correctamente.')
    } else if (modo === 'editar') {
      enviar(`/api/usuarios/${id}`, 'PUT', { nombre, username, rol }, 'Usuario actualizado correctamente.')
    } else {
      enviar('/api/usuarios', 'POST', { nombre, username, password, rol }, 'Usuario creado correctamente.')
    }
  }

  function cambiarEstado() {
    if (estado.activo && (estado.id === usuarioActual.id || esUltimo(estado))) {
      setError('No puedes desactivarte ni desactivar al último administrador activo.')
      return
    }
    enviar(`/api/usuarios/${estado.id}/estado`, 'PATCH', { activo: !estado.activo }, estado.activo ? 'Usuario desactivado correctamente.' : 'Usuario activado correctamente.')
  }

  if (rol !== 'ADMIN') return <p className="error" role="alert">Solo los administradores pueden acceder a Usuarios.</p>

  return (
    <section className="panel">
      <div className="cabecera-productos">
        <h2>Listado de usuarios</h2>
        <button type="button" disabled={cargando || Boolean(errorCarga) || ocupado} onClick={() => abrir('crear')}>Nuevo usuario</button>
      </div>
      {!formulario && error && <p {...avisoProps}>{error}</p>}
      {exito && <p className="exito" role="status">{exito}</p>}
      {formulario && (
        <form {...formProps} className="formulario-producto" onSubmit={guardar}>
          {error && <p {...avisoProps}>{error}</p>}
          <fieldset disabled={ocupado}>
            <legend>{formulario.modo === 'crear' ? 'Nuevo usuario' : formulario.modo === 'password' ? `Cambiar contraseña de ${formulario.username}` : `Editar usuario ${formulario.username}`}</legend>
            <LeyendaObligatorios />
            {formulario.modo !== 'password' && <>
              <label htmlFor="usuario-nombre">Nombre<IndicadorObligatorio /></label>
              <input {...campoProps('usuario-nombre')} id="usuario-nombre" value={formulario.nombre} required maxLength={255} onChange={(evento) => setFormulario({ ...formulario, nombre: evento.target.value })} />
            {errorCampo('usuario-nombre')}
              <label htmlFor="usuario-username">Username<IndicadorObligatorio /></label>
              <input {...campoProps('usuario-username')} id="usuario-username" autoComplete="off" value={formulario.username} required maxLength={255} pattern={'\\S+'} onChange={(evento) => setFormulario({ ...formulario, username: evento.target.value })} />
            {errorCampo('usuario-username')}
              <label htmlFor="usuario-rol">Rol<IndicadorObligatorio /></label>
              <select {...campoProps('usuario-rol')} id="usuario-rol" value={formulario.rol} required onChange={(evento) => setFormulario({ ...formulario, rol: evento.target.value })}>
                <option value="ADMIN">ADMIN</option>
                <option value="EMPLEADO" disabled={formulario.modo === 'editar' && esUltimo(usuarios.find((usuario) => usuario.id === formulario.id) ?? {})}>EMPLEADO</option>
              </select>
            {errorCampo('usuario-rol')}
              {formulario.id === usuarioActual.id && <p>Si cambias tu username o tu rol, tendrás que iniciar sesión de nuevo.</p>}
            </>}
            {formulario.modo !== 'editar' && <>
              <label htmlFor="usuario-password">Nueva contraseña<IndicadorObligatorio /></label>
              <input {...campoProps('usuario-password', 'usuario-password-ayuda')} id="usuario-password" type="password" autoComplete="new-password" value={formulario.password} required minLength={8} maxLength={72} onChange={(evento) => setFormulario({ ...formulario, password: evento.target.value })} />
            {errorCampo('usuario-password')}
              <p id="usuario-password-ayuda">Al menos 8 caracteres y como máximo 72 bytes UTF-8. Algunos caracteres ocupan varios bytes.</p>
            </>}
          </fieldset>
          <div className="botones-formulario">
            <button type="submit" disabled={ocupado}>{ocupado ? 'Guardando...' : 'Guardar'}</button>
            <button type="button" disabled={ocupado} onClick={() => { setFormulario(null); setError('') }}>Cancelar</button>
          </div>
        </form>
      )}
      {estado && <div className="formulario-producto" role="region" aria-labelledby="confirmar-estado">
        <h3 id="confirmar-estado">{estado.activo ? 'Desactivar' : 'Activar'} usuario</h3>
        <p>¿Confirmas que quieres {estado.activo ? 'desactivar' : 'activar'} a «{estado.username}»?</p>
        <div className="botones-formulario">
          <button type="button" disabled={ocupado} onClick={cambiarEstado}>{ocupado ? 'Guardando...' : 'Confirmar'}</button>
          <button type="button" disabled={ocupado} onClick={() => { setEstado(null); setError('') }}>Cancelar</button>
        </div>
      </div>}
      {cargando && <p role="status">Cargando usuarios...</p>}
      {errorCarga && <div role="alert"><p className="error">{errorCarga}</p><button type="button" onClick={() => setRevision((valor) => valor + 1)}>Reintentar consulta</button></div>}
      {!cargando && !errorCarga && <>
        <p>No puedes desactivarte ni desactivar o degradar al último administrador activo.</p>
        <div className="tabla-contenedor" tabIndex="0" role="region" aria-label="Listado de usuarios">
          <table className="tabla-productos">
            <thead><tr><th>ID</th><th>Nombre</th><th>Username</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {usuarios.map((usuario) => <tr key={usuario.id}>
                <td>{usuario.id}</td><td>{usuario.nombre}</td><td>{usuario.username}</td><td>{usuario.rol}</td><td>{usuario.activo ? 'Activo' : 'Inactivo'}</td>
                <td><div className="botones-formulario">
                  <button type="button" disabled={ocupado} onClick={() => abrir('editar', usuario)}>Editar<span className="solo-lectores"> {usuario.username}</span></button>
                  <button type="button" disabled={ocupado} onClick={() => abrir('password', usuario)}>Cambiar contraseña<span className="solo-lectores"> de {usuario.username}</span></button>
                  <button type="button" disabled={ocupado || (usuario.activo && (usuario.id === usuarioActual.id || esUltimo(usuario)))} onClick={() => { setFormulario(null); setError(''); setExito(''); setEstado(usuario) }}>{usuario.activo ? 'Desactivar' : 'Activar'}<span className="solo-lectores"> {usuario.username}</span></button>
                </div></td>
              </tr>)}
              {usuarios.length === 0 && <tr><td colSpan={6}>No hay usuarios registrados.</td></tr>}
            </tbody>
          </table>
        </div>
      </>}
    </section>
  )
}
