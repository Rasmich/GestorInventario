const eventos = new EventTarget()
const mensajeServidor = 'No se ha podido conectar con el servidor. Inténtalo de nuevo más tarde.'
const mensajesEstado = {
  400: 'Revisa los datos introducidos.',
  404: 'No se ha encontrado el recurso solicitado. Recarga los datos e inténtalo de nuevo.',
  409: 'La operación entra en conflicto con los datos actuales. Revisa los datos e inténtalo de nuevo.',
  422: 'Revisa los datos introducidos. No se ha podido completar la operación.',
}

// Un solo lector para conservar detalles y campos en todos los formularios.
// fetchApi sigue siendo responsable de red, sesión, permisos y errores 5xx.
export async function leerRespuesta(respuesta, mensaje = mensajesEstado[respuesta.status]
  ?? `No se ha podido completar la operación (${respuesta.status}).`) {
  if (!respuesta.ok) {
    const datos = await respuesta.json().catch(() => null)
    const detalles = Array.isArray(datos?.errores) ? datos.errores.filter((valor) => typeof valor === 'string') : []
    const detail = typeof datos?.detail === 'string' ? datos.detail : ''
    const error = new Error([detail, ...detalles].filter(Boolean).join(' · ') || mensaje)
    error.status = respuesta.status
    error.campos = datos?.erroresCampos
    throw error
  }
  if (respuesta.status === 204) return null
  try {
    return await respuesta.json()
  } catch (causa) {
    if (causa.name === 'AbortError') throw causa
    throw new Error('El servidor ha devuelto un formato de datos inesperado.', { cause: causa })
  }
}

export function alRecibir401(manejador) {
  const escuchar = (evento) => manejador(evento.detail)
  eventos.addEventListener('sesion-invalida', escuchar)
  return () => eventos.removeEventListener('sesion-invalida', escuchar)
}

export async function fetchApi(ruta, opciones = {}) {
  let respuesta
  try {
    respuesta = await fetch(ruta, opciones)
  } catch (causa) {
    // Cancelar una consulta al navegar o cambiar filtros no es un fallo de conexión.
    if (opciones.signal?.aborted || causa?.name === 'AbortError') throw causa
    const error = new Error(mensajeServidor, { cause: causa })
    error.status = 0
    throw error
  }
  if (respuesta.status >= 500 && respuesta.status <= 599) {
    const error = new Error(mensajeServidor)
    error.status = respuesta.status
    throw error
  }
  if (respuesta.status !== 401 && respuesta.status !== 403) return respuesta

  const autorizacion = new Headers(opciones.headers).get('Authorization')
  if (respuesta.status === 401 && autorizacion) {
    // La identidad de la petición permite ignorar errores de sesiones anteriores.
    eventos.dispatchEvent(new CustomEvent('sesion-invalida', { detail: autorizacion }))
  }
  const datos = await respuesta.json().catch(() => null)
  const mensaje = respuesta.status === 403
    ? 'No tienes permisos para realizar esta operación.'
    : autorizacion ? 'Tu sesión ha caducado o ya no es válida. Inicia sesión de nuevo.' : 'Usuario o contraseña incorrectos.'
  const error = new Error(respuesta.status === 403 ? datos?.detail || mensaje : mensaje)
  error.status = respuesta.status
  throw error
}
