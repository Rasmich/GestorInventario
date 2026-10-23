import assert from 'node:assert/strict'
import { test } from 'node:test'
import { alRecibir401, fetchApi } from '../src/api.js'

const mensajeServidor = 'No se ha podido conectar con el servidor. Inténtalo de nuevo más tarde.'
const opciones = { headers: { Authorization: 'Bearer token-de-prueba' } }

function vigilarSesion(t) {
  const avisos = []
  const cancelar = alRecibir401((autorizacion) => avisos.push(autorizacion))
  t.after(cancelar)
  return avisos
}

test('traduce un fallo de red sin notificar el cierre de sesión', async (t) => {
  const avisos = vigilarSesion(t)
  const causa = new TypeError('Failed to fetch')
  t.mock.method(globalThis, 'fetch', async () => { throw causa })
  await assert.rejects(fetchApi('/api/productos', opciones), (error) => {
    assert.equal(error.message, mensajeServidor)
    assert.equal(error.status, 0)
    assert.equal(error.cause, causa)
    return true
  })
  assert.deepEqual(avisos, [])
})

for (const status of [500, 501, 502, 503, 504, 599]) {
  test(`HTTP ${status}: mensaje comprensible sin consumir HTML ni cerrar sesión`, async (t) => {
    const avisos = vigilarSesion(t)
    const respuesta = new Response('<html>Error del proxy</html>', { status })
    t.mock.method(globalThis, 'fetch', async () => respuesta)
    await assert.rejects(fetchApi('/api/productos', opciones), { message: mensajeServidor, status })
    assert.equal(respuesta.bodyUsed, false)
    assert.deepEqual(avisos, [])
  })
}

for (const status of [400, 409]) {
  test(`HTTP ${status}: conserva el detalle y los errores de validación del backend`, async (t) => {
    const avisos = vigilarSesion(t)
    const datos = status === 400
      ? { detail: 'Revisa los campos indicados.', errores: ['Categoría: Este campo es obligatorio.'] }
      : { detail: 'Ya existe un producto con ese código. Introduce un código diferente.' }
    const respuesta = Response.json(datos, { status })
    t.mock.method(globalThis, 'fetch', async () => respuesta)
    const recibida = await fetchApi('/api/productos', opciones)
    assert.equal(recibida, respuesta)
    assert.deepEqual(await recibida.json(), datos)
    assert.deepEqual(avisos, [])
  })
}

test('401 autenticado: mantiene el mensaje y la notificación de sesión caducada', async (t) => {
  const avisos = vigilarSesion(t)
  t.mock.method(globalThis, 'fetch', async () => Response.json({ detail: 'JWT inválido' }, { status: 401 }))
  await assert.rejects(fetchApi('/api/productos', opciones), {
    status: 401, message: 'Tu sesión ha caducado o ya no es válida. Inicia sesión de nuevo.',
  })
  assert.deepEqual(avisos, ['Bearer token-de-prueba'])
})

test('401 en login: mantiene el mensaje de credenciales sin cerrar otra sesión', async (t) => {
  const avisos = vigilarSesion(t)
  t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 401 }))
  await assert.rejects(fetchApi('/api/auth/login'), {
    status: 401, message: 'Usuario o contraseña incorrectos.',
  })
  assert.deepEqual(avisos, [])
})

test('403: conserva el detalle de permisos y no notifica el cierre de sesión', async (t) => {
  const avisos = vigilarSesion(t)
  t.mock.method(globalThis, 'fetch', async () => Response.json({ detail: 'Se requiere un administrador activo' }, { status: 403 }))
  await assert.rejects(fetchApi('/api/usuarios', opciones), {
    status: 403, message: 'Se requiere un administrador activo',
  })
  assert.deepEqual(avisos, [])
})

test('403 sin JSON: conserva el mensaje de permisos predeterminado', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 403 }))
  await assert.rejects(fetchApi('/api/usuarios', opciones), {
    status: 403, message: 'No tienes permisos para realizar esta operación.',
  })
})

test('las cancelaciones mantienen su identidad y no se muestran como fallos de red', async (t) => {
  const avisos = vigilarSesion(t)
  const controlador = new AbortController()
  controlador.abort()
  const causa = new DOMException('Consulta cancelada', 'AbortError')
  t.mock.method(globalThis, 'fetch', async () => { throw causa })
  await assert.rejects(fetchApi('/api/movimientos', { ...opciones, signal: controlador.signal }),
    (error) => error === causa)
  assert.deepEqual(avisos, [])
})

test('tras un 502 se puede reintentar con las mismas opciones y autorización', async (t) => {
  const avisos = vigilarSesion(t)
  let llamadas = 0
  const envio = { ...opciones, method: 'POST', body: '{"nombre":"Datos del formulario"}' }
  t.mock.method(globalThis, 'fetch', async (ruta, recibidas) => {
    assert.equal(ruta, '/api/categorias')
    assert.equal(recibidas, envio)
    return ++llamadas === 1 ? new Response('', { status: 502 }) : Response.json({ id: 1 })
  })
  await assert.rejects(fetchApi('/api/categorias', envio), { status: 502 })
  assert.deepEqual(await (await fetchApi('/api/categorias', envio)).json(), { id: 1 })
  assert.equal(envio.body, '{"nombre":"Datos del formulario"}')
  assert.deepEqual(avisos, [])
})
