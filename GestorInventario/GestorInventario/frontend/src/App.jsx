import { useErroresFormulario } from './useErroresFormulario'
import { IndicadorObligatorio, LeyendaObligatorios } from './CamposObligatorios'

import { useCallback, useEffect, useState } from 'react'
import { alRecibir401, fetchApi, leerRespuesta } from './api'
import './App.css'
import './theme.css'
import Icono from './Icono'
import Productos from './Productos'
import Movimientos from './Movimientos'
import Categorias from './Categorias'
import Proveedores from './Proveedores'
import Usuarios from './Usuarios'
import Inicio from './Inicio'
import { guardarSesion, recuperarSesion } from './sesion'


function App() {
  const [sesionInicial] = useState(recuperarSesion)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [usuario, setUsuario] = useState(sesionInicial?.usuario ?? null)
  const { error, setError, formProps, avisoProps, campoProps, errorCampo } = useErroresFormulario('login', false)
  const [cargando, setCargando] = useState(false)



  const [seccion, setSeccion] = useState(sesionInicial?.seccion ?? 'inicio')
  const [productos, setProductos] = useState([])
  const [cargandoProductos, setCargandoProductos] = useState(false)
  const [errorProductos, setErrorProductos] = useState('')
  const [revisionProductos, setRevisionProductos] = useState(0)

  useEffect(() => {
    guardarSesion(usuario, seccion)
  }, [usuario, seccion])

  async function iniciarSesion(evento) {
    evento.preventDefault()
    setError('')
    setCargando(true)

    try {
      const respuesta = await fetchApi('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password }),
      })

      const datos = await leerRespuesta(respuesta)
      setUsuario(datos)
      setPassword('')
      setSeccion('inicio')
    } catch (error) {
      setError(error)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    if (!usuario || !['productos', 'movimientos'].includes(seccion)) return

    const controlador = new AbortController()

    async function cargarProductos() {
      setCargandoProductos(true)
      setErrorProductos('')

      try {
        const respuesta = await fetchApi('/api/productos', {
          headers: {
            Authorization: `Bearer ${usuario.token}`,
          },
          signal: controlador.signal,
        })

        const datos = await leerRespuesta(respuesta)
        setProductos(datos)
      } catch (error) {
        if (error.name !== 'AbortError') {
          setErrorProductos(error.message)
        }
      } finally {
        if (!controlador.signal.aborted) {
          setCargandoProductos(false)
        }
      }
    }

    cargarProductos()

    return () => controlador.abort()
  }, [usuario, seccion, revisionProductos])

  function movimientoGuardado(movimiento) {
    setProductos((anteriores) => anteriores.map((producto) =>
      producto.id === movimiento.producto.id ? movimiento.producto : producto))
    setRevisionProductos((revision) => revision + 1)
  }

  const cerrarSesion = useCallback(() => {
    guardarSesion(null)
    setUsuario(null)
    setUsername('')
    setPassword('')
    setProductos([])
    setErrorProductos('')
    setCargandoProductos(false)
    setSeccion('inicio')
  }, [])

  useEffect(() => alRecibir401((autorizacion) => {
    if (usuario && autorizacion === `Bearer ${usuario.token}`) {
      cerrarSesion()
      setError('Tu sesión ha caducado o ya no es válida. Inicia sesión de nuevo.')
    }
  }), [usuario, cerrarSesion, setError])

  function cerrarSesionPorCambio(mensaje) {
    cerrarSesion()
    setError(mensaje)
  }

  function actualizarUsuarioPropio(datos) {
    setUsuario((anterior) => anterior ? { ...anterior, ...datos } : null)
  }

  function productoGuardado(producto) {
    setProductos((anteriores) => anteriores.some((anterior) => anterior.id === producto.id)
      ? anteriores.map((anterior) => anterior.id === producto.id ? producto : anterior)
      : [...anteriores, producto])
  }

  if (!usuario) {
    return (
        <div className="login">
          <form {...formProps} className="login-formulario" onSubmit={iniciarSesion}>
            <h1>Gestor de Inventario</h1>
            <p>Inicia sesión para acceder a la aplicación.</p>

            <LeyendaObligatorios />
            <label htmlFor="username">Usuario<IndicadorObligatorio /></label>
            <input {...campoProps('username')} id="username"
                type="text"
                value={username}
                onChange={(evento) => setUsername(evento.target.value)}
                required
            />
            {errorCampo('username')}

            <label htmlFor="password">Contraseña<IndicadorObligatorio /></label>
            <input {...campoProps('password')} id="password"
                type="password"
                value={password}
                onChange={(evento) => setPassword(evento.target.value)}
                required
            />
            {errorCampo('password')}

            {error && <p {...avisoProps}>{error}</p>}

            <button type="submit" disabled={cargando}>
              {cargando ? 'Entrando...' : 'Iniciar sesión'}
            </button>
          </form>
        </div>
    )
  }

  return (
      <div className="app">
        <aside className="sidebar">
          <div className="sidebar-marca">
            <span className="marca-icono"><Icono nombre="productos" /></span>
            <h2>Gestor de Inventario</h2>
          </div>

          <nav aria-label="Navegación principal">
            <button aria-current={seccion === 'inicio' ? 'page' : undefined} onClick={() => setSeccion('inicio')}><Icono nombre="inicio" />Inicio</button>
            <button aria-current={seccion === 'productos' ? 'page' : undefined} onClick={() => setSeccion('productos')}><Icono nombre="productos" />Productos</button>
            <button aria-current={seccion === 'categorias' ? 'page' : undefined} onClick={() => setSeccion('categorias')}><Icono nombre="categorias" />Categorías</button>
            <button aria-current={seccion === 'proveedores' ? 'page' : undefined} onClick={() => setSeccion('proveedores')}><Icono nombre="proveedores" />Proveedores</button>
            <button aria-current={seccion === 'movimientos' ? 'page' : undefined} onClick={() => setSeccion('movimientos')}><Icono nombre="movimientos" />Movimientos</button>

            {usuario.rol === 'ADMIN' && (
                <button aria-current={seccion === 'usuarios' ? 'page' : undefined} onClick={() => setSeccion('usuarios')}><Icono nombre="usuarios" />Usuarios</button>
            )}
          </nav>
        </aside>

        <main className="contenido">
          <header>
            <div className="cabecera-identidad">
            <h1>{({ usuarios: 'Usuarios', categorias: 'Categorías', proveedores: 'Proveedores', movimientos: 'Movimientos', productos: 'Productos' })[seccion] ?? 'Panel de control'}</h1>
            <p>Bienvenido, {usuario.nombre} — {usuario.rol}</p>
            </div>
            <button className="boton-secundario" onClick={cerrarSesion}><Icono nombre="salir" />Cerrar sesión</button>
          </header>

          {seccion === 'inicio' && (
              <Inicio key={usuario.token} token={usuario.token} />
          )}

          {seccion === 'productos' && (
              <Productos
                  key={usuario.token}
                  token={usuario.token}
                  rol={usuario.rol}
                  productos={productos}
                  cargando={cargandoProductos}
                  errorCarga={errorProductos}
                  alGuardar={productoGuardado}
                  alRecargar={() => setRevisionProductos((revision) => revision + 1)}
              />
          )}
          {seccion === 'movimientos' && (
              <Movimientos
                  token={usuario.token}
                  productos={productos}
                  cargandoProductos={cargandoProductos}
                  errorProductos={errorProductos}
                  alGuardar={movimientoGuardado}
              />
          )}

          {seccion === 'categorias' && <Categorias token={usuario.token} rol={usuario.rol} />}
          {seccion === 'proveedores' && <Proveedores token={usuario.token} rol={usuario.rol} />}
          {seccion === 'usuarios' && (usuario.rol === 'ADMIN'
              ? <Usuarios usuarioActual={usuario} alActualizarPropio={actualizarUsuarioPropio} alCerrarSesion={cerrarSesionPorCambio} />
              : <p className="error" role="alert">Solo los administradores pueden acceder a Usuarios.</p>)}

          {!['inicio', 'productos', 'movimientos', 'categorias', 'proveedores', 'usuarios'].includes(seccion) && (
              <section className="panel">
                <h2>{seccion.charAt(0).toUpperCase() + seccion.slice(1)}</h2>
                <p>Esta sección la desarrollaremos próximamente.</p>
              </section>
          )}
        </main>
      </div>
  )
}

export default App
